"""Reject incompatible fields, enums and removed tags without explicit reservations.

Requires Python protobuf 7.36.1 (tools/mikd/requirements.txt).
The v1 descriptor defines the first release; future changes must not overwrite it.
"""
from pathlib import Path
import tempfile
from google.protobuf import descriptor_pb2
from generate_protocol import ROOT, protoc


RETIRED_ASSISTANT_GAME = {
    "mik.v1.AssistantGameQuery",
    "mik.v1.AssistantGameQuery.Tool",
    "mik.v1.AssistantGameResult",
    "mik.v1.AssistantInventory",
    "mik.v1.AssistantItem",
    "mik.v1.AssistantOnlinePlayers",
    "mik.v1.AssistantPlayer",
    "mik.v1.AssistantWorld",
}


def index_types(files):
    messages, enums = {}, {}

    def visit(prefix, owner, nested):
        for enum in owner.enum_type:
            enums[f"{prefix}.{enum.name}"] = enum
        for message in nested:
            name = f"{prefix}.{message.name}"
            messages[name] = message
            visit(name, message, message.nested_type)

    for file in files.file:
        visit(file.package, file, file.message_type)
    return messages, enums


def field_shape(field, owner):
    oneof = owner.oneof_decl[field.oneof_index].name if field.HasField("oneof_index") else None
    return field.name, field.type, field.type_name, field.label, oneof, field.proto3_optional


def lost_reservations(before, after, name, kind=""):
    for reserved in before.reserved_range:
        if not any(r.start <= reserved.start and r.end >= reserved.end for r in after.reserved_range):
            yield f"{name}: lost reserved {kind}range"
    if not set(before.reserved_name).issubset(after.reserved_name):
        yield f"{name}: lost reserved {kind}name"


def music_contract(files):
    return descriptor_pb2.FileDescriptorSet(file=[
        file for file in files.file if file.name == "mik/music.proto"
    ])


def shared_contract(files):
    """The deployed chat workflow has its own version. Shared identity types remain v1."""
    shared = descriptor_pb2.FileDescriptorSet()
    for file in files.file:
        if file.name in {"mik/music.proto", "mik/chat.proto"}:
            continue
        target = shared.file.add()
        target.CopyFrom(file)
        if file.name == "mik/chat_message.proto":
            messages = [msg for msg in target.message_type
                        if msg.name in {"ChatPlayer", "ChatExternalIdentity"}]
            del target.message_type[:]
            target.message_type.extend(messages)
    return shared


def check(old, new, *, retired_types=frozenset()):
    errors = []
    previous, previous_enums = index_types(old)
    current, current_enums = index_types(new)
    for name in sorted(retired_types & (current.keys() | current_enums.keys())):
        errors.append(f"retired type reappeared: {name}")
    retired_payloads = set()
    retired_enums = set()
    in_use = {field.type_name.lstrip(".") for message in current.values() for field in message.field}
    in_use.update(type_name.lstrip(".") for file in new.file for service in file.service
                  for method in service.method for type_name in (method.input_type, method.output_type))
    for name, before in previous.items():
        after = current.get(name)
        if after is None:
            continue
        fields = {f.number: f for f in after.field}
        for field in before.field:
            candidate = fields.get(field.number)
            if candidate is None:
                if not (field.name in after.reserved_name and any(
                        r.start <= field.number < r.end for r in after.reserved_range)):
                    errors.append(f"{name}.{field.name}: removed tag/name must be reserved")
                elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_MESSAGE:
                    retired_payloads.add(field.type_name.lstrip("."))
                elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_ENUM:
                    owned_enum = field.type_name.lstrip(".")
                    if owned_enum.rpartition(".")[0] == name:
                        retired_enums.add(owned_enum)
                continue
            if field_shape(field, before) != field_shape(candidate, after):
                errors.append(f"incompatible field: {name}.{field.name}")
        errors.extend(lost_reservations(before, after, name))
    for name in sorted(previous.keys() - current.keys()):
        before = previous[name]
        if name in retired_types and name not in in_use:
            continue
        # An empty payload used only by a retired field doesn't need a generated type.
        if name in retired_payloads and name not in in_use and (
                not (before.field or before.nested_type or before.enum_type)):
            continue
        errors.append(f"removed message: {name}")
    for name, before in previous_enums.items():
        after = current_enums.get(name)
        if after is None:
            if (name not in retired_enums and name not in retired_types) or name in in_use:
                errors.append(f"removed enum: {name}")
            continue
        values = {v.number: v.name for v in after.value}
        for value in before.value:
            if values.get(value.number) == value.name:
                continue
            if value.number in values or not (value.name in after.reserved_name and any(
                    r.start <= value.number <= r.end for r in after.reserved_range)):
                errors.append(f"incompatible enum: {name}.{value.name}")
        errors.extend(lost_reservations(before, after, name, "enum "))
    return errors


FIXTURE_DIR = ROOT / "tests/interop"


def check_fixtures():
    groups = (
        ("mik/transport.proto", "mik.v1.Envelope",
         ("hello-v1.bin", "assistant-blocks-v6.hex", "assistant-tool-call-v9.hex", "assistant-tools-ask-v9.hex")),
        ("mik/music.proto", "mik.music.v2.MusicRequest", ("music-v2.hex",)),
    )
    for schema, message, names in groups:
        for name in names:
            frozen = FIXTURE_DIR / name
            source = frozen.with_suffix(".textproto")
            encoded = protoc(f"--encode={message}", schema=schema, input_bytes=source.read_bytes())
            matches = (encoded.hex() == frozen.read_text().strip() if frozen.suffix == ".hex"
                       else encoded == frozen.read_bytes())
            if not matches:
                raise SystemExit(f"{name} differs from its readable fixture")


def read_descriptor(path):
    return descriptor_pb2.FileDescriptorSet.FromString(path.read_bytes())


def main():
    before = read_descriptor(FIXTURE_DIR / "schema-v1.pb")
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "current.pb"
        protoc(f"--descriptor_set_out={path}", "--include_imports")
        current = read_descriptor(path)
    errors = check(shared_contract(before), shared_contract(current), retired_types=RETIRED_ASSISTANT_GAME)
    music = read_descriptor(FIXTURE_DIR / "schema-music-v2.pb")
    errors.extend(check(music, music_contract(current)))
    retired_messages, retired_enums = index_types(music_contract(before))
    current_messages, current_enums = index_types(current)
    if retired_messages.keys() & current_messages.keys() or retired_enums.keys() & current_enums.keys():
        errors.append("Retired music v1 types have reappeared")
    if errors:
        raise SystemExit("\n".join(errors))
    check_fixtures()
    print("MIK protocol field, enum and codec checks passed (shared v1, music v2)")


if __name__ == "__main__":
    main()
