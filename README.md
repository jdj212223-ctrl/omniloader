# Omniloader

**Play Fabric, Quilt and NeoForge mods together in one Minecraft game.**

Minecraft mods are made for different "mod loaders" (Fabric, Quilt, NeoForge, Forge), and normally
you can only use one kind at a time. Omniloader is an add-on for NeoForge that also starts Fabric
and Quilt mods. Put them all in the same `mods` folder and play.

Version 0.1.0, for **Minecraft 1.21.1** (NeoForge 21.1).

![Main Menu Credits, a Fabric mod, drawing its text on the title screen of a NeoForge game](docs/proof-title-screen.png)

*Above: a NeoForge game with 7 mods. The gold and blue text at the top comes from "Main Menu Credits",
a Fabric-only mod, running through Omniloader next to AppleSkin (a NeoForge mod).*

---

## Installing (Mac, Windows, Linux)

1. Open the Minecraft Launcher, Prism Launcher or the Modrinth App at least once.
2. Run the installer:
   - **Mac:** open `Omniloader-Installer-0.1.0.dmg` and double-click **Omniloader Installer**.
     The first time, macOS may say it "can't check it for malicious software", because the app
     isn't signed with a paid Apple certificate. Right-click the app, choose **Open**, then **Open** again.
   - **Windows / Linux:** double-click `omniloader-installer.jar` (needs Java 17 or newer).
3. Tick the launchers you use and press **Install**:
   - **Minecraft Launcher:** installs NeoForge and adds a profile called
     *Omniloader (Fabric + Quilt + NeoForge)* with its own game folder. Your other profiles and
     worlds aren't touched, and a backup of the launcher settings is saved first.
   - **Prism Launcher:** adds an instance called *Omniloader*. Prism downloads NeoForge itself.
   - **Modrinth App:** makes `Omniloader.mrpack` in your Downloads folder and opens it in the
     Modrinth App, which asks you to install it.
4. Press **Open mods folder** and drop in your Fabric, Quilt and NeoForge mods for 1.21.1.
5. Pick the Omniloader profile in your launcher and press **Play**.

Already have NeoForge 1.21.1? You can skip the installer: just put `omniloader.jar` in your `mods` folder.

---

## What works today

| | Status |
|---|---|
| NeoForge mods | ✅ Work as normal (it *is* NeoForge underneath) |
| Fabric mods that don't need Fabric API | ✅ Work, including their mixins (code changes to Minecraft) |
| Quilt mods that don't need Quilt's library (QSL) | ✅ Should work (same path as Fabric; no real Quilt-only mod tested yet) |
| Mods bundled inside Fabric mods | ✅ Unpacked and loaded too |
| Fabric "access wideners" | ✅ Converted to NeoForge's version (access transformers) |
| Mods asking "is mod X installed?" | ✅ Sees Fabric, Quilt *and* NeoForge mods |
| Fabric mods that need **Fabric API** | 🟡 Started. The most-used parts work (see below). Omniloader checks every Fabric API piece a mod uses: if all of them exist, the mod runs; if not, it's switched off with a message naming what's missing, instead of crashing |
| **Forge** mods (the old Forge, not NeoForge) | ❌ Not yet. They're spotted and named in the log |
| Minecraft versions other than 1.21.1 | ❌ Not yet |

### Tested for real

All of these were run in a real game on an Apple Silicon Mac:

- **Alternate Current** (Fabric, redstone): its `/alternatecurrent` command works, and redstone
  wires get the right power levels (15 → 11 → 1 → 0) with its rewritten redstone engine.
- **Main Menu Credits** (Fabric, client): draws its text on the title screen (picture above).
- **Krypton** (Fabric, networking): starts, loads its bundled native library for Apple Silicon,
  and its access widener is converted.
- **FerriteCore** (Fabric, memory saver): all 11 of its mixin sets load with zero errors.
- **Chunky** (Fabric + Fabric API, world pre-generator): its `/chunky` commands work and it
  generated 81 chunks, using Omniloader's Fabric API commands and server events.
- **Clumps** (Fabric + Fabric API): 20 XP orbs of different sizes merged into one, which only Clumps does.
- **Dynamic FPS** (Fabric + Fabric API, client): runs and reacts when the window loses focus.
  This mod writes Minecraft names straight into its mixins, which Omniloader also translates.
- **Nature's Compass** (needs Fabric API networking): switched off with a clear message, no crash.
- **AppleSkin** (NeoForge): loads alongside all of the above, 12 mods in total.
- The installer: Minecraft Launcher profile, Prism instance and Modrinth pack were made and checked
  in test folders. The Mac app finds Java and opens its window.

### Not tested yet (be aware)

- Everything above ran in NeoForge's *development* setup. The normal player setup should behave the
  same way, but hasn't been run start-to-finish yet.
- The installer's "install NeoForge for the official launcher" step downloads and runs NeoForge's
  own installer; that step wasn't run during testing.
- Playing online with Fabric mods that change networking (like Krypton) hasn't been tested.

---

## How it works (short version)

1. **Finding mods.** When NeoForge looks through the `mods` folder, Omniloader checks every jar.
   Fabric (`fabric.mod.json`) and Quilt (`quilt.mod.json`) mods are picked out, plus any mods bundled inside them.
2. **Translating names.** Fabric mods call Minecraft's code by Fabric's own made-up names
   (`class_310`, `method_1234`). NeoForge uses Mojang's real names (`Minecraft`, `tick`).
   Omniloader rewrites every name inside the mod, including the lookup tables its mixins use.
   The name list is built once on first start and saved.
3. **Making it a NeoForge mod.** Each translated mod gets a NeoForge mod description, so NeoForge
   lists it, applies its mixins and shows it on the Mods screen (marked "Fabric mod, running through Omniloader").
   Translated copies are cached in `.omniloader/` in your game folder, so this only happens once per mod.
4. **Starting it.** Omniloader provides Fabric's and Quilt's loader API (`FabricLoader`, `QuiltLoader`,
   mod info, entrypoints) and runs each mod's start-up code (`main`, then `client` or `server`)
   at the point where Fabric would.

## Building it yourself

Needs Java 21.

```bash
./gradlew :loader:jar            # loader/build/libs/omniloader.jar  (put this in mods/)
./gradlew :installer:jar         # installer/build/libs/omniloader-installer.jar
./gradlew :installer:macDmg      # installer/build/mac/Omniloader-Installer-0.1.0.dmg (on a Mac)
tools/fetch-testmods.sh          # downloads the real test mods into testmods/
./gradlew :devrun:runServer      # test server (RCON on port 25599, password omnitest)
./gradlew :devrun:runClient      # test client: saves a title-screen screenshot, then quits
```

Project layout:

- `loader/`: the part that plugs into NeoForge's mod discovery: finds, translates and converts Fabric/Quilt jars.
- `runtime/`: the in-game part: Fabric and Quilt loader API stand-ins and the code that starts the mods.
  It's packed inside `omniloader.jar` and unpacked automatically.
- `installer/`: the installer and the Mac app.
- `devrun/`: a test game that loads everything from `devrun/run*/mods`.
- `tools/`: test helpers (fetch the test mods, send commands to the test server).

## Roadmap

1. ✅ **Loader core**: plugs into NeoForge, finds Fabric/Quilt/Forge/NeoForge jars, including bundled ones.
2. ✅ **Fabric and Quilt**: name translation, mixins, access wideners, loader API, entrypoints.
3. 🟡 **Fabric API**: stand-ins for the most-used parts, built on NeoForge's events.
   This is the big one: most Fabric mods need Fabric API. Done so far:
   - the event system (`Event`, `EventFactory`, phases)
   - commands (`CommandRegistrationCallback`)
   - server start/stop and data pack sync (`ServerLifecycleEvents`)
   - server and client ticks (`ServerTickEvents`, `ClientTickEvents`)
   - key bindings (`KeyBindingHelper`)

   (Data-pack-reload and before/after-save events exist so mods don't crash, but aren't sent yet.)

   Next, by how many popular mods use them: resource reload listeners, networking
   (`PayloadTypeRegistry`, `ServerPlayNetworking`, `ClientPlayNetworking`), client commands,
   screen events, HUD rendering, item groups, then the renderer API.
4. ⏳ **Forge mods**: rewrite `net.minecraftforge` code to NeoForge's equivalents.

For scale: Sinytra Connector, the best-known project that runs Fabric mods on NeoForge, took years
to get where it is. Omniloader takes the same basic approach and is at the start of that road.

## Credits and licences

- Fabric's "intermediary" name list (CC0) is bundled. Mojang's name list is downloaded from Mojang
  on first start (or reused from your computer) and isn't shipped, as Mojang's licence requires.
- Omniloader doesn't include code from Fabric Loader, Quilt Loader or Sinytra Connector. The Fabric and Quilt
  API classes are written from scratch to match the public method names mods use.
- Not affiliated with Mojang, Microsoft, Fabric, Quilt or NeoForge.
