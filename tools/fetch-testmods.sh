#!/bin/sh
# Downloads the real mods we test with (from Modrinth) into testmods/.
cd "$(dirname "$0")/../testmods" || exit 1
get() { # slug loader
  url=$(curl -sfA omniloader-dev "https://api.modrinth.com/v2/project/$1/version?loaders=%5B%22$2%22%5D&game_versions=%5B%221.21.1%22%5D" \
    | python3 -I -c 'import json,sys; print(json.load(sys.stdin)[0]["files"][0]["url"])')
  echo "$1 ($2): $url"; curl -sfLO "$url"
}
get alternate-current fabric      # Fabric, server side: redstone rewrite + /alternatecurrent command (mixins)
get main-menu-credits fabric      # Fabric, client side: text on the title screen (mixin)
get appleskin neoforge            # NeoForge: food HUD
