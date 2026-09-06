# Contributing to Extended Note Block

[Home](README.md) · [Development guide](docs/DEVELOPMENT.md) · [Security policy](SECURITY.md)

Thank you for helping improve Extended Note Block. This repository targets Minecraft 26.2 and ships three distinct program editions; always state which edition you tested.

## Report an issue

Use the matching GitHub issue form for a bug, feature request or audio-quality problem. Include the ENB version, Minecraft version, Java version, loader or Paper/Purpur build, exact reproduction steps and relevant logs. Remove server addresses, access tokens and personal information before attaching logs.

Security vulnerabilities must be reported privately through the process in [SECURITY.md](SECURITY.md), not through a public issue.

## Prepare a change

1. Create a focused branch from the current development baseline.
2. Follow the build and preparation order in the [development guide](docs/DEVELOPMENT.md). Preparation scripts intentionally update generated sources.
3. Add or update tests and documentation with the implementation.
4. Run the checks relevant to your change.

```bash
python3 scripts/check_documentation.py
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew test
./gradlew -p bridge test
```

The complete client build requires JDK 25. Resource-pack generation also requires FFmpeg and uses the reviewed SoundFont in `tools/audio/`.

## Pull requests

Open pull requests against `main`. Keep unrelated formatting or generated-file changes out of the patch, explain user-visible behavior, and state what was actually tested. Do not use a `release:` commit prefix; that prefix is reserved for maintainers publishing verified artifacts from `port/26.2`.

Before submitting, confirm that:

- Full Fabric and Paper Client remain mutually exclusive.
- Paper Client does not register custom blocks or items.
- Paper/Purpur behavior still works for players without a client Mod.
- New assets have a clear license and are recorded in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) when required.
- User-facing changes are reflected in the changelog and current documentation.

By contributing, you agree that your contribution is provided under this repository's [MIT License](LICENSE).
