#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src" / "client" / "java"

for path in CLIENT.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    original = text

    # Minecraft 26.2 moved screen ownership to Minecraft.gui.
    text = re.sub(r"(?<!\.gui)\.setScreen\(", ".gui.setScreen(", text)
    text = text.replace("minecraft.screen", "minecraft.gui.screen()")
    text = text.replace("this.minecraft.screen", "this.minecraft.gui.screen()")
    text = text.replace("Minecraft.getInstance().screen", "Minecraft.getInstance().gui.screen()")
    text = text.replace("context.client().screen", "context.client().gui.screen()")

    # Toast manager moved to Minecraft.gui.
    text = text.replace(".getToastManager()", ".gui.toastManager()")

    # ChatFormatting#getColor was removed in 26.2. Use the vanilla legacy red ARGB value.
    text = text.replace("ChatFormatting.RED.getColor()", "0xFFFF5555")

    # The Paper bridge client must not initialize the full content mod just to
    # obtain the namespace constant. Keep SoundPackManager registry-independent.
    if path.name == "SoundPackManager.java":
        text = text.replace("import com.atemukesu.extendednoteblock.ExtendedNoteBlock;\n", "")
        text = text.replace("ExtendedNoteBlock.MOD_ID", '"extendednoteblock"')

    if text != original:
        path.write_text(text, encoding="utf-8")
        print(f"patched {path.relative_to(ROOT)}")

# Minecraft#setScreen no longer exists in 26.2, so this old mixin target cannot be applied.
# The pre-launch check is non-essential; disable only this mixin until it is reimplemented on Gui#setScreen.
mixins = ROOT / "src" / "client" / "resources" / "extendednoteblock.client.mixins.json"
if mixins.exists():
    text = mixins.read_text(encoding="utf-8")
    original = text
    text = re.sub(r'\s*"MinecraftClientMixin",?\n', "\n", text)
    # Repair a possible missing comma after removing the entry.
    text = text.replace('"OptionsScreenMixin"\n\t\t"SoundEventMixin"', '"OptionsScreenMixin",\n\t\t"SoundEventMixin"')
    if text != original:
        mixins.write_text(text, encoding="utf-8")
        print(f"patched {mixins.relative_to(ROOT)}")
