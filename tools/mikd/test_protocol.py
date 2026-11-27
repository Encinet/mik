import unittest
from google.protobuf import descriptor_pb2
from check_protocol import check, music_contract, shared_contract


class ProtocolCompatibilityTest(unittest.TestCase):
    def setUp(self):
        self.old = descriptor_pb2.FileDescriptorSet()
        file = self.old.file.add(name="test.proto", package="test")
        message = file.message_type.add(name="Request")
        message.oneof_decl.add(name="body")
        message.field.add(name="value", number=10, type=9, label=1, oneof_index=0)
        enum = file.enum_type.add(name="Role")
        enum.value.add(name="UNKNOWN", number=0)
        enum.value.add(name="CONTROL", number=1)
        self.new = descriptor_pb2.FileDescriptorSet()
        self.new.CopyFrom(self.old)

    def test_additive_change_is_allowed(self):
        self.new.file[0].message_type[0].field.add(name="revision", number=11, type=4, label=1)
        self.assertEqual([], check(self.old, self.new))

    def test_field_shape_and_oneof_changes_are_rejected(self):
        for key, value in [("type", 5), ("name", "renamed"), ("label", 3)]:
            with self.subTest(key=key):
                changed = descriptor_pb2.FileDescriptorSet()
                changed.CopyFrom(self.new)
                setattr(changed.file[0].message_type[0].field[0], key, value)
                self.assertTrue(check(self.old, changed))
        self.new.file[0].message_type[0].field[0].ClearField("oneof_index")
        self.assertTrue(check(self.old, self.new))

    def test_retired_fields_require_number_and_name_reservation(self):
        self.old.file[0].message_type.add(name="Empty")
        field = self.old.file[0].message_type[0].field[0]
        field.type = descriptor_pb2.FieldDescriptorProto.TYPE_MESSAGE
        field.type_name = ".test.Empty"
        message = self.new.file[0].message_type[0]
        del message.field[:]
        message.reserved_range.add(start=10, end=11)
        self.assertTrue(check(self.old, self.new))
        message.reserved_name.append("value")
        self.assertEqual([], check(self.old, self.new))
        self.old.file[0].message_type[1].field.add(name="still_data", number=1, type=9, label=1)
        self.assertEqual(["removed message: test.Empty"], check(self.old, self.new))

    def test_music_contract_is_checked_separately_from_shared_types(self):
        old_music = self.old.file.add(name="mik/music.proto", package="mik.v1")
        old_music.message_type.add(name="MusicReply").field.add(name="sources", number=4, type=9, label=1)
        current_music = self.new.file.add(name="mik/music.proto", package="mik.music.v2")
        current_music.message_type.add(name="MusicReply").field.add(name="media", number=4, type=12, label=1)
        self.assertEqual([], check(shared_contract(self.old), shared_contract(self.new)))
        self.assertEqual(["removed message: mik.v1.MusicReply"],
                         check(music_contract(self.old), music_contract(self.new)))
        self.new.file[0].message_type[0].field[0].name = "renamed"
        self.assertTrue(check(shared_contract(self.old), shared_contract(self.new)))

    def test_enum_renumbering_cannot_be_silently_accepted(self):
        self.new.file[0].enum_type[0].value[1].number = 2
        self.assertTrue(check(self.old, self.new))

    def test_nested_enum_can_be_retired_only_after_reserving_its_field(self):
        previous = self.old.file[0].message_type[0]
        previous.enum_type.add(name="Tool").value.add(name="UNSPECIFIED", number=0)
        previous.field.add(name="tool", number=12, type=14, label=1, type_name=".test.Request.Tool")
        current = self.new.file[0].message_type[0]
        self.assertIn("removed enum: test.Request.Tool", check(self.old, self.new))
        current.reserved_name.append("tool")
        current.reserved_range.add(start=12, end=13)
        self.assertEqual([], check(self.old, self.new))
        del self.new.file[0].enum_type[:]
        self.assertEqual(["removed enum: test.Role"], check(self.old, self.new))

    def test_reserved_ranges_cannot_be_reused(self):
        self.old.file[0].message_type[0].reserved_range.add(start=3, end=9)
        self.assertTrue(check(self.old, self.new))
        self.old.file[0].message_type[0].reserved_range.clear()
        self.old.file[0].enum_type[0].reserved_range.add(start=7, end=9)
        self.assertTrue(check(self.old, self.new))


if __name__ == "__main__":
    unittest.main()
