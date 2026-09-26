#!/usr/bin/env python3
"""Localize player-visible Paper messages after all source preparation steps."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'bridge/src/main/java/com/goldenegggovo/extendednoteblock/bridge'


def closing_parenthesis(source: str, start: int) -> int:
    depth = 1
    quoted = False
    escaped = False
    for index in range(start, len(source)):
        char = source[index]
        if quoted:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char == '(':
            depth += 1
        elif char == ')':
            depth -= 1
            if depth == 0:
                return index
    raise ValueError('Unclosed sendMessage call')


def prepare(source: str) -> str:
    marker = '.sendMessage('
    positions = []
    start = 0
    while (index := source.find(marker, start)) != -1:
        argument = index + len(marker)
        end = closing_parenthesis(source, argument)
        if not source[argument:end].lstrip().startswith('EnbLanguage.text('):
            positions.append((argument, end))
        start = end + 1
    for argument, end in reversed(positions):
        source = source[:argument] + 'EnbLanguage.text(' + source[argument:end] + ')' + source[end:]
    return source


def main() -> None:
    bridge = SOURCE / 'ExtendedNoteBlockBridge.java'
    source = bridge.read_text(encoding='utf-8')
    source = source.replace('        saveDefaultConfig();\n',
                            '        saveDefaultConfig();\n        EnbLanguage.load(this);\n', 1) if '        EnbLanguage.load(this);' not in source else source
    source = source.replace('                reloadConfig();\n',
                            '                reloadConfig();\n                EnbLanguage.load(this);\n', 1) if source.count('EnbLanguage.load(this);') == 1 else source
    source = source.replace('meta.setDisplayName(type.displayName);',
                            'meta.setDisplayName(EnbLanguage.text(type.displayName));')
    source = source.replace('"ExtendedNoteBlock Bridge item",',
                            'EnbLanguage.text("ExtendedNoteBlock Bridge item"),')
    source = source.replace('"Vanilla carrier: minecraft:" + type.carrier.name().toLowerCase(Locale.ROOT)',
                            'EnbLanguage.text("Vanilla carrier: minecraft:" + type.carrier.name().toLowerCase(Locale.ROOT))')
    source = source.replace('ceMeta.setCustomModelDataComponent(ceModel); stack.setItemMeta(ceMeta);',
                            'ceMeta.setCustomModelDataComponent(ceModel); ceMeta.setDisplayName(EnbLanguage.text(type.displayName)); stack.setItemMeta(ceMeta);')
    bridge.write_text(prepare(source), encoding='utf-8')
    for name in ('EnbCraftEngine.java', 'EnbCombinedPack.java'):
        path = SOURCE / name
        if path.exists():
            path.write_text(prepare(path.read_text(encoding='utf-8')), encoding='utf-8')
    print('Prepared Paper language loading and player messages')


if __name__ == '__main__':
    main()
