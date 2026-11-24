#!/bin/sh
# Downloads the real mods we test with (from Modrinth) into testmods/.
cd "$(dirname "$0")/../testmods" || exit 1
get() { # slug loader
  url=$(curl -sfA omniloader-dev "https://api.modrinth.com/v2/project/$1/version?loaders=%5B%22$2%22%5D&game_versions=%5B%221.21.1%22%5D" \
    | python3 -I -c 'import json,sys; print(json.load(sys.stdin)[0]["files"][0]["url"])')
  echo "$1 ($2): $url"; curl -sfLO "$url"
}
get alternate-current fabric      # server: redstone rewrite + /alternatecurrent command (mixins)
get main-menu-credits fabric      # client: text on the title screen (mixin)
get krypton fabric                # networking; access widener + bundled native library
get ferrite-core fabric           # 11 mixin configs, mixin plugins asking about other mods
get chunky fabric                 # Fabric API: commands + server lifecycle/tick events
get clumps fabric                 # Fabric API: its own events; merges XP orbs
get dynamic-fps fabric            # Fabric API: client ticks; mixins with names written in (no refmap)
get natures-compass fabric        # needs Fabric API networking: should be switched off politely
get appleskin neoforge            # NeoForge mod
