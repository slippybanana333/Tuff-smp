# Tuff SMP

A Fabric mod for Minecraft **1.21.11** (Java 21): 14 legendary 3D items (Molten Greataxe, Cutlass of Doom, Sculk Mace, Frost Longbow,
Cloud Lance, Harbinger of Death, Anaklusmos, Sculk Cannon, Strange Cape, Silver's Spawn Orb and four polished-tuff armor pieces),
obtained only through the Shrine block, plus five themed GUIs (`/tuffsmp gui <ocean|lava|sculk|arctic|end|off>`).

- Build the mod: `./gradlew build` -> `build/libs/tuff-smp-1.0.0.jar`
- Resource pack: `python3 tools/build_resourcepack.py` -> `build/TuffSMP-ResourcePack.zip`
- Self-test: headless server on Linux/Mac: `bash tools/server_selftest.sh` (~250 checks). On Windows: `cmd /c gradlew.bat runServer --no-daemon --console=plain` with an empty `run/selftest-on` file (the mod runs the self-test by itself ~15 s after start)
- Start with `CLAUDE.md`, then `MOD_KNOWLEDGE_BASE.md` for how everything works.

Mod id `shrinecraft`, command `/tuffsmp`. All rights reserved (see `LICENSE`).
Mojang/Microsoft reference art used by the Python art tools is not included (see `.gitignore`).
