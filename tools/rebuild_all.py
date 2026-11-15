#!/usr/bin/env python3
"""
rebuild_all.py - the ONE command to run after any model/texture tweak.

    python3 tools/rebuild_all.py            # relayout UVs -> repaint textures -> check -> resource pack zip
    python3 tools/rebuild_all.py --jar      # ...and also ./gradlew build (the mod jar in build/libs)
    python3 tools/rebuild_all.py --viewer   # ...and also the optional model viewer + Blockbench copies (no longer delivered)
"""
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
T = os.path.join(ROOT, "tools")


def run(*cmd, cwd=ROOT):
    print("\n>>>", " ".join(cmd))
    r = subprocess.run(cmd, cwd=cwd)
    if r.returncode != 0:
        print("STEP FAILED:", " ".join(cmd))
        sys.exit(r.returncode)


def main():
    py = sys.executable
    run(py, os.path.join(T, "relayout_uv.py"))
    run(py, os.path.join(T, "paint_textures.py"))
    run(py, os.path.join(T, "check_models.py"))
    run(py, os.path.join(T, "build_resourcepack.py"))
    if "--jar" in sys.argv:
        gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "./gradlew")
        run(gradlew, "build")
    if "--viewer" in sys.argv:                  # the old model viewer / Blockbench export are optional extras now
        run(py, os.path.join(T, "export_bbmodel.py"))
        run(py, os.path.join(T, "build_viewer.py"))
    print("\nAll done. Mod: build/libs/tuff-smp-1.0.0.jar   Resource pack: build/TuffSMP-ResourcePack.zip")


if __name__ == "__main__":
    main()
