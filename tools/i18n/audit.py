#!/usr/bin/env python3
"""Audit the locale inventory; Java tests validate and resolve actual Fluent syntax."""

from dataclasses import dataclass
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
LANG_DIR = ROOT / "src/main/resources/lang"
ENTRY = re.compile(r"^(-?[a-z][a-z0-9-]*)\s*=(.*)$")
ATTRIBUTE = re.compile(r"^\s+\.([a-z][a-z0-9-]*)\s*=", re.MULTILINE)
VARIABLE = re.compile(r"\$([a-zA-Z][a-zA-Z0-9_]*)")
REFERENCE = re.compile(r"\{\s*(-?[a-z][a-z0-9-]*)(?:\.([a-z][a-z0-9-]*))?(?=\s*(?:[}(]|->))")
SELECTOR = re.compile(r"\{\s*\$([a-zA-Z][a-zA-Z0-9_]*)\s*->")
SHORTHAND = re.compile(r"\$\d+")
PSEUDO_PLURAL = re.compile(r"\w+\((?:s|es|n|en|ов|ей|ів)\)|을\(를\)|이\(가\)|은\(는\)|와\(과\)|\(으\)로")
TECHNICAL = re.compile(
    r"\b(?:Plasmo Voice|km/h|RPM|TPS|MSPT|AFK|PVP|UUID|MIK|Ping|ms|Online)\b",
    re.IGNORECASE,
)
# These are also native French / Dutch, or consist entirely of actual command syntax.
SHARED_PROSE = {
    "fr_fr": {"governance-minutes", "social-duration-minutes"},
    "nl_nl": {"nametag-online-editor", "social-admin-help"},
}


@dataclass
class Entry:
    line: int
    body: str

    @property
    def attributes(self):
        return set(ATTRIBUTE.findall(self.body))


def read_resource(path, errors):
    entries = {}
    current = None
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = ENTRY.match(line)
        if match:
            key, body = match.groups()
            if key in entries:
                errors.append(f"{path.name}:{line_number}: duplicate {key}")
            entries[key] = Entry(line_number, body.strip())
            current = key
        elif line.startswith((" ", "\t")) and current:
            entries[current].body += "\n" + line
        elif line.strip() and not line.startswith("#"):
            errors.append(f"{path.name}:{line_number}: unrecognized top-level line")
            current = None
        else:
            current = None
    return entries


def arguments(entries, key, visited=None):
    """Follow public message references; term parameters belong to the locale itself."""
    visited = set() if visited is None else visited
    if key in visited:
        return set()
    visited.add(key)
    body = entries[key].body
    names = set(VARIABLE.findall(body)) - {"person", "case"}
    # Rich components have a separate numeric selector for the same logical argument.
    selectors = set(SELECTOR.findall(body))
    names = {name.removesuffix("Value") if name in selectors and name.endswith("Value")
             and name.removesuffix("Value") in names else name for name in names}
    for reference, _ in REFERENCE.findall(body):
        if not reference.startswith("-") and reference in entries:
            names |= arguments(entries, reference, visited)
    return names


def english_words(body):
    body = re.sub(r"\{[^{}]*\}|<[^>]*>|\[.*?\]", " ", body)
    body = TECHNICAL.sub(" ", body)
    return re.findall(r"[A-Za-z]{2,}", body)


def audit():
    errors = []
    resources = {path.stem: read_resource(path, errors) for path in sorted(LANG_DIR.glob("*.ftl"))}
    enum_source = (ROOT / "src/main/java/org/encinet/mik/shared/i18n/Language.java").read_text(encoding="utf-8")
    locales = set(re.findall(r'^\s+[A-Z_]+\("([a-z_]+)"', enum_source, re.MULTILINE))
    if resources.keys() != locales:
        errors.append(f"Locale files differ from Language.java: {sorted(resources.keys() ^ locales)}")
    if "en_us" not in resources:
        errors.append("Missing reference locale en_us.ftl")
        return errors

    reference = resources["en_us"]
    reference_keys = set(reference)
    catalog_source = (ROOT / "src/main/java/org/encinet/mik/shared/i18n/Message.java").read_text(encoding="utf-8")
    catalog = re.findall(r"^    ([A-Z][A-Z0-9_]*)(?:,|;)$", catalog_source, re.MULTILINE)
    catalog_keys = [name.lower().replace("_", "-") for name in catalog]
    public_keys = [key for key in reference if not key.startswith("-")]
    if catalog_keys != public_keys:
        errors.append("Message.java differs from the canonical FTL keys or their functional order")
    callers = set()
    for path in (ROOT / "src/main/java").rglob("*.java"):
        if path.name != "Message.java":
            callers.update(re.findall(r"\bMessage\.([A-Z][A-Z0-9_]*)\b", path.read_text(encoding="utf-8")))
    unused = set(catalog) - callers
    if unused:
        errors.append(f"Registered messages without production callers: {sorted(unused)}")
    contracts = {key: arguments(reference, key) for key in reference if not key.startswith("-")}
    print("locale  messages  terms  numeric messages  person messages")
    for locale, entries in resources.items():
        keys = set(entries)
        if keys != reference_keys:
            errors.append(f"{locale}: missing {sorted(reference_keys - keys)}, extra {sorted(keys - reference_keys)}")
        elif list(entries) != list(reference):
            errors.append(f"{locale}: entry order differs from en_us.ftl")

        numeric_messages = 0
        person_messages = 0
        for key, entry in entries.items():
            where = f"{locale}.ftl:{entry.line}: {key}"
            if SHORTHAND.search(entry.body):
                errors.append(f"{where}: use named Fluent variables, not $0 shorthand")
            if PSEUDO_PLURAL.search(entry.body):
                errors.append(f"{where}: replace bracketed endings with grammatical variants")
            for target, attribute in REFERENCE.findall(entry.body):
                if target not in entries:
                    errors.append(f"{where}: missing reference {target}")
                elif attribute and attribute not in entries[target].attributes:
                    errors.append(f"{where}: missing attribute {target}.{attribute}")
            if key in reference and entry.attributes != reference[key].attributes:
                errors.append(f"{where}: attributes differ from en_us.ftl")
            if key.startswith("-"):
                continue
            selectors = set(SELECTOR.findall(entry.body))
            numeric_messages += bool(selectors - {"person"})
            person_messages += "person" in selectors
            if key in contracts and arguments(entries, key) != contracts[key]:
                errors.append(f"{where}: arguments {sorted(arguments(entries, key))} differ from {sorted(contracts[key])}")
            if (locale != "en_us" and key in reference
                    and " ".join(entry.body.split()) == " ".join(reference[key].body.split())
                    and key not in SHARED_PROSE.get(locale, set())
                    and len(english_words(entry.body)) >= 2):
                errors.append(f"{where}: unchanged multiword English text")

        term_count = sum(key.startswith("-") for key in entries)
        print(f"{locale:7} {len(entries) - term_count:8} {term_count:6} {numeric_messages:17} {person_messages:16}")
    return errors


def main():
    errors = audit()
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print(f"Translation audit failed: {len(errors)} issue(s).", file=sys.stderr)
        return 1
    print("Translation inventory, references, arguments and English-copy audit passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
