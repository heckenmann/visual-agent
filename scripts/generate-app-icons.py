#!/usr/bin/env python3
"""Render the canonical SVG into the existing desktop icon resource paths.

Requires Inkscape and Pillow. Run from any directory with
`python3 scripts/generate-app-icons.py` after editing the source SVG.
"""

from pathlib import Path
import shutil
import subprocess
import tempfile

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "modules/ui/src/main/resources/icons/visual-agent.svg"
PNG = ROOT / "modules/ui/src/main/resources/icons/visual-agent.png"
COMPOSE_PNG = ROOT / "modules/desktop/src/main/composeResources/drawable/visual_agent.png"
PACKAGING = ROOT / "modules/desktop/packaging/icons"
ICO_SIZES = (16, 24, 32, 48, 64, 128, 256)


def main():
    """Overwrite all derived icons from the same SVG without changing resource names."""
    with tempfile.TemporaryDirectory(prefix="visual-agent-icons-") as directory:
        rendered = Path(directory) / "visual-agent.png"
        subprocess.run(
            [
                "inkscape",
                "--batch-process",
                str(SOURCE),
                "--export-type=png",
                "--export-width=1024",
                "--export-height=1024",
                f"--export-filename={rendered}",
            ],
            check=True,
        )
        with Image.open(rendered) as decoded:
            image = decoded.convert("RGBA")
        if image.size != (1024, 1024):
            raise ValueError("The application icon must render at 1024 × 1024 pixels")
        image.save(PNG, optimize=True)
        shutil.copyfile(PNG, COMPOSE_PNG)
        image.save(
            PACKAGING / "visual-agent.ico",
            sizes=[(size, size) for size in ICO_SIZES],
        )
        image.save(PACKAGING / "visual-agent.icns")
    print("Updated UI PNG, Compose PNG, Windows ICO, and macOS ICNS from the F2 SVG.")


if __name__ == "__main__":
    main()
