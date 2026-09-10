#!/usr/bin/env python3
"""Build an ENB-only CraftEngine resource installation from the official ENB pack.

No server configuration, saved music, other plugins, or CraftEngine binaries are
included. Extract this alongside the bridge JAR, preserving existing plugin data.
"""
import argparse
from pathlib import Path, PurePosixPath
import zipfile
from resource_pack_assets import CARRIER_ITEMS

ROOT = Path(__file__).resolve().parents[1]
PREFIX = 'plugins/CraftEngine/resources/enb/'

def build(resource_pack: Path, output: Path, version: str):
    entries = {}
    with zipfile.ZipFile(resource_pack) as archive:
        if archive.testzip() is not None:
            raise ValueError('Invalid resource pack CRC')
        required = {'pack.mcmeta', 'assets/extendednoteblock/models/block/c.json',
                    'assets/extendednoteblock_listener/sounds.json'}
        if not required.issubset(archive.namelist()):
            raise ValueError('Expected the complete official ENB resource pack')
        for info in archive.infolist():
            if info.is_dir() or info.filename == 'pack.mcmeta':
                continue
            path = PurePosixPath(info.filename)
            if path.is_absolute() or '..' in path.parts or '\\' in info.filename:
                raise ValueError('Unsafe resource pack path: ' + info.filename)
            enb = len(path.parts) > 2 and path.parts[:2] in (
                ('assets', 'extendednoteblock'), ('assets', 'extendednoteblock_listener'))
            carrier = info.filename in {f'assets/minecraft/items/{item}.json' for item in CARRIER_ITEMS}
            metadata = info.filename in {'README.txt', 'THIRD_PARTY_NOTICES.txt', 'enb_server_pack.json', 'pack.png'}
            if not enb and not carrier and not metadata:
                raise ValueError('Non-ENB resource pack entry: ' + info.filename)
            entries[PREFIX + 'resourcepack/' + info.filename] = archive.read(info)
    entries[PREFIX + 'configuration/enb.yml'] = (ROOT/'craftengine/configuration/enb.yml').read_bytes()
    entries[PREFIX + 'pack.yml'] = (
        'namespace: enb\nname: ExtendedNoteBlock\nversion: ' + version
        + '\nauthor: GoldenEggOVO\ndescription: ENB original models and listener sounds\n').encode('utf-8')
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    print(f'Built ENB CraftEngine resources: {output} ({len(entries)} files)')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resource-pack', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--version', required=True)
    args = parser.parse_args()
    build(args.resource_pack, args.output, args.version)
