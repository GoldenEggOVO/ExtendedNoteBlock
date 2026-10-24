"""The shared source preparation must leave already prepared sources unchanged."""

from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class SourcePreparationTest(unittest.TestCase):
    def test_preparation_does_not_wrap_projection_entities_repeatedly(self):
        writer = Path("src/main/java/com/atemukesu/extendednoteblock/nbs/NbsProjectionWriter.java")
        archive = Path("src/main/resources/assets/extendednoteblock/extendednoteblock_default.zip")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative in (writer, archive, *(Path("scripts") / name for name in (
                    "prepare_26_2_sources.py", "prepare_26_3_sources.py", "resource_pack_assets.py"))):
                destination = root / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / relative, destination)
            command = [sys.executable, str(root / "scripts/prepare_26_3_sources.py")]
            subprocess.run(command, check=True, capture_output=True)
            first = (root / writer).read_bytes(), (root / archive).read_bytes()
            subprocess.run(command, check=True, capture_output=True)
            self.assertEqual(first, ((root / writer).read_bytes(), (root / archive).read_bytes()))
            text = (root / writer).read_text(encoding="utf-8")
            self.assertNotIn("if (!PAPER_SAFE_EXPORT) {\n                if (!PAPER_SAFE_EXPORT)", text)


if __name__ == "__main__":
    unittest.main()
