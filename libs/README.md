# Optional bundled mod: UI-Utils Advanced

To ship **one** installable JAR that also contains [UI-Utils Advanced](https://github.com/FrannnnDev/ui-utils-advanced) (fork of [Coderx-Gamer/ui-utils](https://github.com/Coderx-Gamer/ui-utils)):

1. Download the **matching Minecraft version** JAR from the [releases page](https://github.com/FrannnnDev/ui-utils-advanced/releases).
2. Save it as **`ui-utils-advanced.jar`** in this `libs/` folder (same folder as this file).
3. Run `./gradlew build` — the addon copies it to `META-INF/jars/` and declares it in `fabric.mod.json` (`jars`), which Fabric Loader treats as a nested mod (same idea as Loom `include`, but raw file JARs cannot use Loom’s `include(files(...))` API).

Users then only need your addon JAR in `mods/`; Fabric Loader will still load UI-Utils as a separate mod from the nested JAR.

## License / attribution (required)

UI-Utils Advanced is distributed under **CC BY-NC-SA 4.0** (see that repo’s `LICENSE`). If you bundle it:

- Keep **attribution** to **Coderx-Gamer** (original) and **FrannnnDev** (fork) in your distribution docs (see `THIRD_PARTY.md` in the repo root).
- **Non-commercial** and **share-alike** terms apply to that component — confirm they fit how you distribute your addon.

The `libs/*.jar` files are **gitignored** so you do not commit the binary; each builder supplies their own JAR if they want bundling.
