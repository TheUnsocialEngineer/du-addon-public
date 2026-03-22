# Third-party software

## When you bundle UI-Utils Advanced (`libs/ui-utils-advanced.jar`)

If your built DupersUnited addon JAR was produced **with** `libs/ui-utils-advanced.jar` present, it **embeds** [UI-Utils Advanced](https://github.com/FrannnnDev/ui-utils-advanced) via Fabric jar-in-jar.

| Component | Authors / upstream | License | Source |
|-----------|-------------------|---------|--------|
| UI-Utils (original) | Coderx-Gamer | See upstream repo | https://github.com/Coderx-Gamer/ui-utils |
| UI-Utils Advanced (fork) | FrannnnDev | **CC BY-NC-SA 4.0** (see their `LICENSE`) | https://github.com/FrannnnDev/ui-utils-advanced |

You must comply with **CC BY-NC-SA 4.0** for that embedded component (attribution, non-commercial use as defined there, share-alike on adaptations, etc.). This file is a pointer only — read the upstream `LICENSE` for the full terms.

If you build **without** `libs/ui-utils-advanced.jar`, no UI-Utils code is bundled and this section does not apply to your artifact.
