#!/usr/bin/env python3
"""Embed a released resource-pack URL and SHA-1 into a Paper plugin JAR."""

from __future__ import annotations

import argparse
import re
import tempfile
import zipfile
from pathlib import Path

URL_TOKEN = "__ENB_RESOURCE_PACK_URL__"
SHA1_TOKEN = "__ENB_RESOURCE_PACK_SHA1__"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("jar", type=Path)
    parser.add_argument("url")
    parser.add_argument("sha1")
    return parser.parse_args()


def configure_jar(jar: Path, url: str, sha1: str) -> None:
    if not jar.is_file():
        raise ValueError(f"Paper plugin JAR not found: {jar}")
    if not url.startswith("https://") or not url.isascii():
        raise ValueError("Resource-pack URL must be an ASCII HTTPS URL")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", sha1):
        raise ValueError("Resource-pack SHA-1 must contain 40 hexadecimal characters")

    with zipfile.ZipFile(jar, "r") as source:
        config = source.read("config.yml").decode("utf-8")
        if URL_TOKEN not in config or SHA1_TOKEN not in config:
            raise ValueError("Paper plugin config.yml does not contain release placeholders")
        config = config.replace(URL_TOKEN, url).replace(SHA1_TOKEN, sha1.lower())

        with tempfile.NamedTemporaryFile(
            prefix=jar.stem + "-", suffix=".jar", dir=jar.parent, delete=False
        ) as handle:
            temporary = Path(handle.name)
        try:
            with zipfile.ZipFile(temporary, "w", allowZip64=True) as target:
                for info in source.infolist():
                    data = config.encode("utf-8") if info.filename == "config.yml" else source.read(info.filename)
                    target.writestr(info, data)
            temporary.replace(jar)
        finally:
            if temporary.exists():
                temporary.unlink()

    with zipfile.ZipFile(jar, "r") as check:
        configured = check.read("config.yml").decode("utf-8")
        if URL_TOKEN in configured or SHA1_TOKEN in configured:
            raise ValueError("Resource-pack placeholders remain after JAR configuration")
        if url not in configured or sha1.lower() not in configured:
            raise ValueError("Configured resource-pack URL/hash could not be verified")


def main() -> None:
    args = parse_args()
    try:
        configure_jar(args.jar, args.url, args.sha1)
    except (OSError, ValueError, zipfile.BadZipFile, KeyError) as error:
        raise SystemExit(str(error)) from error
    print(args.jar)


if __name__ == "__main__":
    main()
