import hashlib
import subprocess
import sys
from collections.abc import Iterator
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from xml.etree import ElementTree

NAMESPACE = {"m": "https://schema.gradle.org/dependency-verification"}
WORKERS = 16


def attribute(element: ElementTree.Element, key: str) -> str:
    value = element.get(key)
    if value is None:
        sys.exit(f"{element.tag} in the verification metadata has no {key}")
    return value


def files(metadata: str) -> Iterator[tuple[str, set[str]]]:
    for component in (
        ElementTree.parse(metadata)
        .getroot()
        .iterfind("m:components/m:component", NAMESPACE)
    ):
        group, name, version = (
            attribute(component, key) for key in ("group", "name", "version")
        )
        for artifact in component.iterfind("m:artifact", NAMESPACE):
            sums = {
                attribute(element, "value")
                for element in artifact.iter()
                if element.tag.endswith("sha256")
            }
            yield (
                f"{group.replace('.', '/')}/{name}/{version}/{attribute(artifact, 'name')}",
                sums,
            )


def download(url: str) -> bytes | None:
    result = subprocess.run(["curl", "-fsSL", url], capture_output=True, check=False)
    return result.stdout if result.returncode == 0 else None


def fetch(mirror: Path, sources: list[str], path: str, sums: set[str]) -> bool:
    for source in sources:
        body = download(f"{source}/{path}")
        if body is not None and hashlib.sha256(body).hexdigest() in sums:
            target = mirror / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(body)
            return True
    return False


def main() -> None:
    metadata, mirror, *sources = sys.argv[1:]
    entries = list(files(metadata))
    with ThreadPoolExecutor(WORKERS) as pool:
        written = pool.map(lambda entry: fetch(Path(mirror), sources, *entry), entries)
        missing = [
            path for (path, _), done in zip(entries, written, strict=True) if not done
        ]
    if missing:
        sys.exit("no source serves the pinned bytes of " + ", ".join(missing))


main()
