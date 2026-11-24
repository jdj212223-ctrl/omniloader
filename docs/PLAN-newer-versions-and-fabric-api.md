# Plan: newer Minecraft versions + more Fabric API

## Why Vampirism 2.0 won't run

Vampirism 2.0 (`Vampirism-26.1.2-2.0.0-beta.1.jar`) is a **NeoForge mod for Minecraft 26.1.2**.
Omniloader right now only runs on **Minecraft 1.21.1**.

Mod loaders weren't the problem: Vampirism is already a NeoForge mod. The problem is the **Minecraft
version**. A mod made for 26.1.2 calls game code that doesn't exist in 1.21.1, so no loader can run it there.
"Run a newer mod on an older game" isn't realistic. The real fix is to **move Omniloader up to 26.1.2**,
so Vampirism 2.0 runs natively (it's NeoForge) and Fabric mods run next to it.

Good news: this is *easier* than 1.21.1 was. From 26.1 on, Mojang ships Minecraft with its real names
(no scrambled names). Fabric mods for 26.1 use those same names, so the whole "translate the names"
step mostly disappears.

## Step 1: Omniloader for Minecraft 26.1.2 (makes Vampirism 2.0 + Fabric mods work together)

1. Make a second build target: NeoForge `26.1.2.126` (already in the Gradle cache on this Mac),
   next to the 1.21.1 one. Same code, separate jar: `omniloader-26.1.2.jar`.
2. Check what changed in NeoForge's mod-finding code (FancyModLoader) between 4.x and the 26.1 version,
   and update the 3 plug-in classes (`OmniLocator`, `OmniModReader`, `OmniNestedLocator`).
3. Name translation: for 26.1 Fabric mods, skip it (names already match). Keep it only for 1.21.1.
4. Re-check the NeoForge "protected fields" list (`NeoFieldGetters`) for 26.1.
5. Test in a dev game: Vampirism 2.0 + one simple Fabric 26.1 mod, same as the 1.21.1 proof.
6. Then test in the real "Vampirism + MCP" Modrinth instance (a **copy**, never the one you play).

## Step 2: more Fabric API (most Fabric mods need it)

In order of how many popular mods use them (measured from the top ~300 Fabric mods):

1. Resource reload listeners (`ResourceManagerHelper`, `IdentifiableResourceReloadListener`)
2. Networking (`PayloadTypeRegistry`, `ServerPlayNetworking`, `ClientPlayNetworking`, `PacketSender`, connection events)
3. Client commands (`ClientCommandRegistrationCallback`)
4. Screen events (`ScreenEvents`, `ScreenMouseEvents`, `Screens`)
5. HUD + world rendering hooks (`HudRenderCallback`, `WorldRenderEvents`)
6. Item groups (`ItemGroupEvents`), particles, block render layers
7. Last and hardest: the renderer API (`Renderer`, `QuadEmitter`, `FabricBakedModel`)

Each piece gets tested with a real mod that needs it: Nature's Compass (networking + item groups)
is already waiting, since Omniloader switches it off today.

## Suggested order

Step 1 first (it's what gets Vampirism 2.0 going), then Step 2 on 26.1.2, where new work matters most.
1.21.1 keeps working as-is.
