#!/usr/bin/env python3
"""Validate repository documentation without third-party Python packages."""

from __future__ import annotations

import re
import sys
import unicodedata
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
IGNORED_PARTS = {".git", ".gradle", "build", "__pycache__"}
ASSET_SUFFIXES = {".gif", ".jpeg", ".jpg", ".png", ".svg", ".webp"}
LINK_PATTERN = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
HEADING_PATTERN = re.compile(r"^#{1,6}\s+(.+?)\s*#*\s*$", re.MULTILINE)


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def current_versions() -> tuple[str, str, str]:
    properties = read_properties(ROOT / "gradle.properties")
    bridge = (ROOT / "bridge" / "build.gradle").read_text(encoding="utf-8")
    match = re.search(r"^version\s*=\s*'([^']+)'", bridge, re.MULTILINE)
    if not match:
        raise ValueError("bridge/build.gradle does not declare a single-quoted version")
    return properties["mod_version"], match.group(1), properties["minecraft_version"]


def markdown_files() -> list[Path]:
    files: list[Path] = []
    for path in ROOT.rglob("*.md"):
        if not any(part in IGNORED_PARTS for part in path.relative_to(ROOT).parts):
            files.append(path)
    return sorted(files)


def github_slug(heading: str) -> str:
    value = re.sub(r"<[^>]+>", "", heading.strip().lower())
    value = re.sub(r"[`*_~]", "", value)
    kept: list[str] = []
    for character in value:
        category = unicodedata.category(character)
        if character in "-_ " or not category.startswith(("P", "S")):
            kept.append(character)
    return re.sub(r"\s+", "-", "".join(kept)).strip("-")


def heading_slugs(path: Path) -> set[str]:
    counts: dict[str, int] = {}
    slugs: set[str] = set()
    text = path.read_text(encoding="utf-8")
    for heading in HEADING_PATTERN.findall(text):
        base = github_slug(heading)
        duplicate = counts.get(base, 0)
        counts[base] = duplicate + 1
        slugs.add(base if duplicate == 0 else f"{base}-{duplicate}")
    return slugs


def split_local_target(raw_target: str) -> tuple[str, str] | None:
    target = raw_target.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1]
    if target.startswith(("http://", "https://", "mailto:", "data:")):
        return None
    # The repository uses no local paths containing spaces followed by a title.
    target = target.split(maxsplit=1)[0]
    path, separator, fragment = target.partition("#")
    return unquote(path), unquote(fragment) if separator else ""


def collect_errors() -> list[str]:
    errors: list[str] = []
    files = markdown_files()
    referenced_assets: set[Path] = set()

    for source in files:
        text = source.read_text(encoding="utf-8")
        for match in LINK_PATTERN.finditer(text):
            local = split_local_target(match.group(1))
            if local is None:
                continue
            relative, fragment = local
            destination = source if not relative else (source.parent / relative).resolve()
            line = text[:match.start()].count("\n") + 1
            if not destination.exists():
                errors.append(
                    f"{source.relative_to(ROOT)}:{line}: missing local target {match.group(1)!r}"
                )
                continue
            if destination.is_file() and destination.suffix.lower() in ASSET_SUFFIXES:
                referenced_assets.add(destination)
            if fragment and destination.is_file() and destination.suffix.lower() == ".md":
                if fragment not in heading_slugs(destination):
                    errors.append(
                        f"{source.relative_to(ROOT)}:{line}: missing heading #{fragment} "
                        f"in {destination.relative_to(ROOT)}"
                    )

    asset_root = ROOT / "docs" / "assets"
    existing_assets = {
        path.resolve()
        for path in asset_root.rglob("*")
        if path.is_file() and path.suffix.lower() in ASSET_SUFFIXES
    }
    for asset in sorted(existing_assets - referenced_assets):
        errors.append(f"{asset.relative_to(ROOT)}: documentation asset is not referenced")

    mod_version, server_version, minecraft_version = current_versions()
    release_tag = f"v{mod_version}-mc{minecraft_version}"
    required_text: dict[str, tuple[str, ...]] = {
        "README.md": (
            f"Full Fabric / Paper Client {mod_version}",
            f"Paper Server {server_version}",
            release_tag,
        ),
        "docs/README.md": (mod_version, server_version, minecraft_version),
        "docs/README_zh-cn.md": (mod_version, server_version, minecraft_version),
        "docs/README_ja-jp.md": (mod_version, server_version, minecraft_version),
        "docs/INSTALLATION.md": (
            f"ExtendedNoteBlock-Full-Fabric-{mod_version}-mc{minecraft_version}.jar",
            f"ExtendedNoteBlock-Paper-Server-{server_version}-mc{minecraft_version}.jar",
            release_tag,
        ),
        "docs/ROADMAP.md": (mod_version, server_version),
    }
    for relative, expected_values in required_text.items():
        text = (ROOT / relative).read_text(encoding="utf-8")
        for expected in expected_values:
            if expected not in text:
                errors.append(f"{relative}: missing current version marker {expected!r}")

    release_note = ROOT / "docs" / "releases" / f"{mod_version}.md"
    if not release_note.is_file():
        errors.append(f"docs/releases/{mod_version}.md: current release note is missing")
    release_index = (ROOT / "docs" / "releases" / "README.md").read_text(encoding="utf-8")
    for article in sorted((ROOT / "docs" / "releases").glob("*.md")):
        if article.name != "README.md" and f"({article.name})" not in release_index:
            errors.append(f"docs/releases/README.md: missing {article.name}")

    notice = ROOT / "THIRD_PARTY_NOTICES.md"
    if not notice.is_file():
        errors.append("THIRD_PARTY_NOTICES.md: root third-party notice is missing")
    elif "GeneralUser GS 2.0.2" not in notice.read_text(encoding="utf-8"):
        errors.append("THIRD_PARTY_NOTICES.md: GeneralUser GS notice is missing")

    return errors


def main() -> None:
    errors = collect_errors()
    if errors:
        print("Documentation validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        raise SystemExit(1)
    mod_version, server_version, minecraft_version = current_versions()
    print(
        f"Documentation OK: {len(markdown_files())} Markdown files; "
        f"Full/Client {mod_version}; Server {server_version}; Minecraft {minecraft_version}"
    )


if __name__ == "__main__":
    main()
