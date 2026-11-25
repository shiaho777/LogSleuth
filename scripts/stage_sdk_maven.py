import re
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

GROUP = "io.github.shiaho777.logsleuth"
ARTIFACT = "logsleuth-sdk"
ARTIFACT_PATH = GROUP.replace(".", "/") + "/" + ARTIFACT
ARCHIVE = re.compile(r"logsleuth-sdk-([0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?)-maven\.zip")
SUFFIXES = (".aar", ".module", ".pom", "-sources.jar")
CHECKSUMS = ("", ".md5", ".sha1", ".sha256", ".sha512")
MAX_BYTES = 32 * 1024 * 1024


def stage_archive(archive, destination):
    match = ARCHIVE.fullmatch(archive.name)
    if match is None:
        raise ValueError(f"Unexpected SDK archive name: {archive.name}")
    version = match[1]
    files = {f"{ARTIFACT}-{version}{suffix}" for suffix in SUFFIXES}
    allowed = {filename + checksum for filename in files for checksum in CHECKSUMS}
    prefix = f"{ARTIFACT_PATH}/{version}/"
    target = destination / prefix

    with ZipFile(archive) as bundle:
        selected = [
            entry for entry in bundle.infolist()
            if not entry.is_dir() and entry.filename.startswith(prefix)
        ]
        names = [entry.filename.removeprefix(prefix) for entry in selected]
        if len(names) != len(set(names)) or not files <= set(names) <= allowed:
            raise ValueError(f"Invalid Maven artifact files in {archive.name}")
        if sum(entry.file_size for entry in selected) > MAX_BYTES:
            raise ValueError(f"Oversized Maven artifact in {archive.name}")
        target.mkdir(parents=True, exist_ok=False)
        for entry, name in zip(selected, names):
            with bundle.open(entry) as source, (target / name).open("xb") as output:
                shutil.copyfileobj(source, output)
    return version


def write_metadata(destination, versions):
    def version_key(version):
        base, _, suffix = version.partition("-")
        return tuple(map(int, base.split("."))), not suffix, suffix

    versions = sorted(versions, key=version_key)
    if not versions:
        return
    root = ET.Element("metadata")
    ET.SubElement(root, "groupId").text = GROUP
    ET.SubElement(root, "artifactId").text = ARTIFACT
    versioning = ET.SubElement(root, "versioning")
    ET.SubElement(versioning, "latest").text = versions[-1]
    stable = [version for version in versions if "-" not in version]
    if stable:
        ET.SubElement(versioning, "release").text = stable[-1]
    elements = ET.SubElement(versioning, "versions")
    for version in versions:
        ET.SubElement(elements, "version").text = version
    ET.indent(root)
    ET.ElementTree(root).write(
        destination / ARTIFACT_PATH / "maven-metadata.xml", encoding="utf-8", xml_declaration=True
    )


def main():
    destination = Path(sys.argv[1])
    versions = [stage_archive(Path(archive), destination) for archive in sys.argv[2:]]
    write_metadata(destination, versions)


if __name__ == "__main__":
    main()
