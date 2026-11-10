import re
import sys
from collections.abc import Iterable

HEADER = re.compile(r"^(\w+)(?: - .*)?$")
TREE = re.compile(r"^[ |]*[+\\]--- ")
NOT_RESOLVED = "(n)"


def sections(lines: Iterable[str]) -> dict[str, list[str]]:
    found: dict[str, list[str]] = {}
    current: list[str] | None = None
    for line in lines:
        header = HEADER.match(line)
        if header:
            current = found.setdefault(header.group(1), [])
        elif current is not None and TREE.match(line):
            current.append(line)
    return found


def resolved(lines: Iterable[str], module: str) -> set[str]:
    pattern = re.compile(
        r"(?<![\w.-])" + re.escape(module) + r"(?::(\S+))?(?: -> (\S+))?(?=\s|$)"
    )
    resolved_lines = (
        line for line in lines if not line.rstrip().endswith(NOT_RESOLVED)
    )
    return {
        chosen or requested
        for line in resolved_lines
        for requested, chosen in pattern.findall(line)
    }


def locked(path: str, module: str, configuration: str) -> set[str]:
    versions = set()
    with open(path) as lockfile:
        for line in lockfile:
            coordinate, _, configurations = line.strip().partition("=")
            name, _, version = coordinate.rpartition(":")
            if name == module and configuration in configurations.split(","):
                versions.add(version)
    return versions


def problems(graph: dict[str, list[str]], lockfiles: list[str], pin: str) -> list[str]:
    configuration, *coordinates = pin.split()
    if configuration not in graph:
        return [f"no configuration {configuration} in gradle dependencies"]
    found = []
    for coordinate in coordinates:
        module, _, version = coordinate.rpartition(":")
        versions = resolved(graph[configuration], module)
        if versions != {version}:
            found.append(
                f"{configuration} resolves {module} to {sorted(versions)}, expected {version}"
            )
        found.extend(
            f"{path} locks {module} {sorted(listed)} for {configuration}, expected {version}"
            for path in lockfiles
            if (listed := locked(path, module, configuration)) != {version}
        )
    return found


def main() -> None:
    report, committed, written, *pins = sys.argv[1:]
    with open(report) as dependencies:
        graph = sections(dependencies.read().splitlines())
    found = [
        problem
        for pin in pins
        for problem in problems(graph, [committed, written], pin)
    ]
    if found:
        sys.exit("\n".join(found))


main()
