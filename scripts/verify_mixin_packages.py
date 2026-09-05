#!/usr/bin/env python3
"""Reject JAR layouts that Mixin would refuse to load at runtime.

Mixin owns the configured package AND every child package. Ordinary entrypoints,
screens and helpers must live outside that tree, even when javac accepts them.
"""

import json
import sys
import zipfile


def verify_mixin_packages(jar: zipfile.ZipFile) -> None:
    names = set(jar.namelist())
    metadata = json.loads(jar.read("fabric.mod.json"))
    entrypoints = []
    for entries in metadata.get("entrypoints", {}).values():
        for entry in entries:
            value = entry if isinstance(entry, str) else entry["value"]
            entrypoints.append(value.split("::", 1)[0])

    for entry in metadata.get("mixins", []):
        config_name = entry if isinstance(entry, str) else entry["config"]
        config = json.loads(jar.read(config_name))
        package = config.get("package", "").rstrip(".")
        if not package:
            raise ValueError(f"{config_name}: mixins must use a dedicated package")
        prefix = package.replace(".", "/") + "/"
        declared = {
            prefix + name.replace(".", "/")
            for side in ("mixins", "client", "server")
            for name in config.get(side, [])
        }
        for class_name in declared:
            if class_name + ".class" not in names:
                raise ValueError(f"{config_name}: missing mixin class {class_name}")
        for entrypoint in entrypoints:
            if entrypoint.startswith(package + "."):
                raise ValueError(f"{config_name}: entrypoint {entrypoint} is inside a Mixin-owned package")
        ordinary = sorted(
            name for name in names
            if name.startswith(prefix) and name.endswith(".class")
            and name[:-6].split("$", 1)[0] not in declared
        )
        if ordinary:
            raise ValueError(f"{config_name}: ordinary classes inside a Mixin-owned package: {ordinary}")


if __name__ == "__main__":
    for path in sys.argv[1:]:
        with zipfile.ZipFile(path) as jar:
            verify_mixin_packages(jar)
        print(f"Mixin package ownership verified: {path}")
