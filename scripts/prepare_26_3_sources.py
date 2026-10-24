#!/usr/bin/env python3
"""Apply the shared source preparation and Minecraft 26.3 pack metadata."""

from pathlib import Path
import runpy

runpy.run_path(str(Path(__file__).with_name("prepare_26_2_sources.py")), run_name="__main__")
