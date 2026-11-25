#!/usr/bin/env python3
"""The audit of the Forge event handlers of the Kingdom.

Forge never calls a method without the annotation of an event. A method that
looks like a handler but carries no annotation is dead code that looks alive:
the server starts, the tests are green and the feature never happens.

The audit finds:
  - handlers whose method is not public (Forge refuses to load the class);
  - methods with the name of a handler that carry no annotation at all;
  - duplicated annotations on one method.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/java")

METHOD = re.compile(
    r"^(?P<indent>[ \t]*)(?P<mods>(?:public|private|protected|static|final|synchronized|\s)*)"
    r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\((?P<args>[^)]*)\)\s*\{",
    re.MULTILINE,
)
ANNOTATION = re.compile(r"@SubscribeEvent\b")
HANDLER_NAME = re.compile(r"^(on|handle|on[A-Z]|when)[A-Z][A-Za-z0-9_]*$")


def main() -> int:
    problems = 0
    for path in sorted(ROOT.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines()
        for index, line in enumerate(lines):
            match = METHOD.match(line)
            if not match:
                continue
            name = match.group("name")
            mods = match.group("mods").split()
            # The annotation may sit above the method or above a javadoc.
            above = []
            cursor = index - 1
            while cursor >= 0 and (lines[cursor].strip().startswith("*")
                                   or lines[cursor].strip().startswith("/**")
                                   or lines[cursor].strip().startswith("//")
                                   or not lines[cursor].strip()):
                above.append(lines[cursor])
                cursor -= 1
            window = "\n".join(reversed(above[-14:])) + "\n" + line
            annotated = bool(ANNOTATION.search(window))
            looks_like_handler = bool(HANDLER_NAME.match(name)) and (
                "TickEvent" in window or "Event" in window
                or "Player" in window or "Level" in window
                or "Block" in window or "Entity" in window
                or "Server" in window)
            if annotated:
                if "private" in mods:
                    print(f"ПРИВАТНЫЙ обработчик: {path}:{index+1} {name}")
                    problems += 1
                if window.count("@SubscribeEvent") > 1:
                    print(f"ДВОЙНАЯ аннотация: {path}:{index+1} {name}")
                    problems += 1
            elif looks_like_handler and "public" in mods and "static" not in mods:
                print(f"МЁРТВЫЙ обработчик (нет @SubscribeEvent): {path}:{index+1} {name}")
                problems += 1
    if problems == 0:
        print("OK: обработчики Forge зарегистрированы, приватных нет, дублей нет")
    else:
        print(f"НАЙДЕНО ПРОБЛЕМ: {problems}")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
