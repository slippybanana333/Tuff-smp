# Tuff SMP - Task list & progress log

> Live checklist for the current session. Updated after every step.
> If the agent loses context, read this file, then CLAUDE.md.

## Task list (FINAL - all done)

- [x] 1. Anaklusmos: remove duplicate "Riptide V" tooltip line (code + lang + knowledge base updated)
- [x] 2. Block enchanting on all 14 custom items (table + anvil + books); built-in enchants stay
  - `.enchantable(N)` removed (anaklusmos/frost_longbow/sculk_cannon/sculk_mace) AND `.component(DataComponentTypes.ENCHANTABLE, null)` added after `.sword()` (cutlass), `.spear()` (cloud_lance), `.pickaxe()` (harbinger), `.armor()` (TuffArmorItem) - those helpers AUTO-ADD enchantable(15) from ToolMaterial.NETHERITE. MoltenGreataxeItem is now a plain Item (AxeItem's constructor re-adds it) with the vanilla strip/scrape/wax useOnBlock copied. `data/minecraft/tags/item/enchantable/*` additions deleted.
  - NEW mixin `AnvilScreenHandlerMixin` (+ `ForgingScreenHandlerAccessor`): the anvil refuses EVERY shrinecraft item (a pre-enchanted legendary passes vanilla's `canHaveEnchantments` gate, so books still applied through the plain tags that must stay for gameplay). Registered in shrinecraft.mixins.json.
  - Verified against real 1.21.11 sources (genSources): table gate = isEnchantable(); anvil gate = canHaveEnchantments + supported-items tags; admin `/enchant` still works (accepted); grindstone can still STRIP enchants (warned).
  - Selftest section Q: 15 stacks table-refuse, anvil book on Cutlass = empty/cost 0, two maces cannot combine, vanilla control still works.
- [x] 3. Cloud Lance first-person pose ("oriented weird"): rotation X 41.84 -> 18.0, translation Y 5.6 -> 3.0, in model json + tools/display_overrides.json; verified visually in the in-browser viewer (tip on screen, mild up angle, lower, side-on)
- [x] 4. Cutlass of Doom leak: NO leak exists (verified in real 1.21.11 sources: getBonusAttackDamage is only asked of the main-hand weapon item). New selftest H2 case: arm Doom Surge for real, switch to a netherite sword, swing -> exactly baseline damage.
- [x] 5. Cloud Lance "readjusted" = covered by task 3 (20s dash cooldown unchanged)
- [x] 6. Display Editor page: viewer/index.html + viewer/display_editor_artifact.html rebuilt locally (claude.ai artifact republish = owner-side)
- [x] 7. Remove GUI auto completely: ZoneDetector.java deleted; zones.json / zones_mask.png / zones_check_map.png / tools/zones_from_map.py / tools/zones_from_drawing.py deleted; GuiCommand = ocean|lava|sculk|arctic|end|off only; GuiSettings DYNAMIC removed (old configs load as OFF); server guiHint tab-completion trimmed; gametest zone steps removed. Sculk-underground issue moot.
- [x] 8. Sculk Mace tiers (owner ladder): t1 Density I, t2 Density II, t3 Wind Burst I, t4 Density III, t5 Wind Burst II + shockwave unlock. Selftest expectations updated.
- [x] 9. Sculk shockwave = radial blast: SculkWaveManager rewritten - instant 6 hp armor-ignoring damage to everything alive within WAVE_RADIUS=4 blocks of the thing hit (owner + trusted + spectators spared) + expanding visual sculk ring. WAVE_RANGE/WAVE_WIDTH replaced by WAVE_RADIUS (radius 4.0 = my pick, TWEAK ME).
- [x] 10. Rebuilt deliverables: build/libs/tuff-smp-1.0.0.jar (5,299,633 B) + build/TuffSMP-ResourcePack.zip (1,938 files) - includes the 5s Deep Freeze pin, 20s lance, flat armor that were already on disk.
- [x] 11. Tests & checks: `gradlew build` OK; `compileGametestJava` OK; `check_models.py` 116 ok / 0 failures; `rebuild_all.py` OK; server selftest **255 passed, 0 failed** (run on Windows via the new `run/selftest-on` file trigger; docs updated in CLAUDE.md + MOD_KNOWLEDGE_BASE.md + README.md).

## Decisions from the owner (answered 2026-11-14)

- "Oriented weird" item = **Cloud Lance**, first person: pointed up too much, tilted toward player, goes off-screen top; should sit slightly further down.
- Cutlass bug = "another weapon hit harder while buff was on".
- Enchant block scope = **all 14 custom items**, table AND anvil/books.
- **GUI auto: remove completely** (no zone detection, no zones.json, sculk-underground rule moot).
- Sculk Mace tiers: t1=Density 1, t2=Density 2, t3=Wind Burst 1, t4=Density 3, t5=Wind Burst 2 + shockwave.
- Shockwave should be a **radial blast** damaging everything in the blast radius.
- Rebuild jar + resource pack at the end: yes.

## Assumptions to report to the owner

1. Sculk Wave blast radius = **4 blocks** (`WAVE_RADIUS`, TWEAK ME constant in SculkMaceItem).
2. The anvil refuses ALL mod items (no repair/renaming either - they are unbreakable, so nothing is lost).
3. Admin `/enchant` still works on items vanilla tags cover (op-level tool).
4. The grindstone can still strip built-in enchantments (vanilla) - do NOT grind a legendary.
5. The Display Editor artifact must be re-published by the owner on claude.ai (no access from here).
6. 6 hp radial damage lands on top of the melee hit (vanilla hurt/invulnerability frames may reduce the blast damage on the exact target that was just meleed).
7. Old `shrinecraft_gui.json` configs saved with the removed "dynamic" mode load as vanilla (off).

## Progress log (newest last)

- 2026-11-14: Task 1 done - removed `tooltip.shrinecraft.anaklusmos.1` from AnaklusmosItem.appendTooltip + en_us.json; MOD_KNOWLEDGE_BASE.md bug note updated.
- 2026-11-14: Task 2 investigation: 4 items had `.enchantable(N)`; custom items also added to vanilla `enchantable/*` tags.
- 2026-11-15: GUI auto removed (task 7); gradle caches set up on this machine (first build here); genSources decompile obtained.
- 2026-11-15: Tasks 8+9 done (tier ladder + radial blast); task 3 done (lance pose via viewer iteration; playwright installed; vanilla reference art regenerated from the local Minecraft 1.21.11 jar because this machine never had the gitignored `tools/reference` art; `make_vanilla_refs.py` made cross-platform); task 6 done (viewer pages rebuilt).
- 2026-11-15: Task 2 done: enchantable stripped everywhere (incl. the auto-added ones), tags deleted, AnvilScreenHandlerMixin added (accessor pattern - @Shadow cannot reach inherited ForgingScreenHandler fields), selftest section Q. MoltenGreataxeItem rewritten as plain Item with copied vanilla strip behaviour.
- 2026-11-15: Task 4 done: no leak; new H2 switch-weapon case.
- 2026-11-15: Selftest on Windows: bash script (FIFOs) hangs -> added `SelfTest.registerAutoRun()` triggered by an empty `run/selftest-on` file (or -Dtuffsmp.selftest=1); ran the full selftest: **255 passed, 0 failed**.
- 2026-11-15: Task 11 done: gradle build + compileGametestJava + check_models (116 ok) + rebuild_all; `rebuild_all.py --jar` patched to use gradlew.bat on Windows; docs updated (CLAUDE.md, MOD_KNOWLEDGE_BASE.md incl. a full 2026-11-15 session entry, README.md).
- Temp helper tools kept on purpose: tools/_pose_iter.js, tools/_pose_ref.js (viewer pose iteration), tools/_selftest.ps1 (older Windows selftest harness - superseded by the selftest-on file, kept for reference).

## Tester round 2 (2026-11-15, after the first delivery) - ALL FIXED + VERIFIED IN THE REAL GAME

Key discovery: the in-browser viewer's THIRD-PERSON view does not reproduce the real game's hold (it showed our lance identical to vanilla's when the real game holds ours vertically vs vanilla's diagonal). The client gametest RUNS ON THIS WINDOWS MACHINE (real window, ~1.5 min, `TUFF_ONLY=...` step filter, screenshots in build/run/clientGameTest/screenshots) and was used to verify everything.

- [x] T1. Trident "weird orientation" - third person now uses the vanilla trident rotation [0, -90, 55] with a grip-preserving translation (t1 = t0 + (R0-R1)@(S*(grip-8)), tools/_pin_calc.py). Verified: holds diagonally like vanilla.
- [x] T2. Cutlass slanted in inventory - gui display now upright [0,0,0] fitted scale 0.559 (tools/_gui_calc.py), model json + display_overrides.json. Verified via gametest "gui zoom".
- [x] T3. Armor named "Tuff ..." - display names in en_us.json: Tuff Helmet/Chestplate/Leggings/Boots (ids stay shrine_*).
- [x] T4. Cloud Lance "backwards" in third person - now the vanilla netherite spear rotation [5, 270, -40] with grip-preserving translation. Verified: holds like vanilla.
- [x] T5. Sculk Cannon "floating" in hand - base (uncharged) third person now vanilla uncharged crossbow rotation [-90, 0, -60], translation re-derived FROM the verified charged pose (charged/drawing states untouched). Verified: hangs at the side like vanilla.
- [x] Rebuilt deliverables again (jar + resource pack + viewer pages) and re-ran everything: check_models 116 ok, selftest running for the final pass.
- First-person poses unchanged this round (lance first-person fix from the earlier round stands).
