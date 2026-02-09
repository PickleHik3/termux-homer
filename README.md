# Termux Homer (Frankenstein Launcher)

This is a personal proof-of-concept I vibecoded for myself.

It combines ideas from Termux Monet and TEL to make a modern Android launcher workflow that is basically just Termux.

I do not claim credit for upstream work. If a real Android developer wants to take this further, please do.

## Credits

- Termux app: https://github.com/termux/termux-app
- Termux Monet: https://github.com/Termux-Monet/termux-monet
- TEL project/wiki: https://github.com/t-e-l/wiki

## Included Files

Everything below is bundled in this repo under `resources/`:

| Type | Path |
| --- | --- |
| Fish config | `resources/config/fish/config.fish` |
| Tmux config | `resources/config/tmux/.tmux.conf` |
| Termux properties | `resources/config/termux/termux.properties` |
| Unexpected Keyboard layout | `resources/config/termux/unexp-keyb-layout.txt` |
| btop restore script | `resources/scripts/restore_btop.sh` |
| tmux status script (kew ticker) | `resources/scripts/statusbar/kew-now-playing` |
| tmux status script (weather ticker) | `resources/scripts/statusbar/weather-cache` |
| `termux-restart` helper | `resources/bin/termux-restart` |
| Pacman bootstrap reference | `resources/optional/termux-init/bootstrap.md` |
| Neovim Termux LSP config | `resources/optional/termux-init/nvim/termux.lua` |
| Crush LSP + MCP config example | `resources/optional/termux-init/crush/crush.example.json` |
| Shell package module reference | `resources/optional/termux-init/modules/packages.sh` |
| Fish shell module reference | `resources/optional/termux-init/modules/shell.sh` |
| Fish plugins list reference | `resources/optional/termux-init/fish/fish_plugins` |

## User Guide

### 1) Install baseline dependencies

```sh
pkg update && pkg upgrade -y
pkg install -y fish tmux termux-api git curl zoxide
```

For the fuller dependency set, see:
- `resources/optional/termux-init/modules/packages.sh`
- `resources/optional/termux-init/modules/shell.sh`
- `resources/optional/termux-init/fish/fish_plugins`

### 2) Copy configs and helpers

```sh
mkdir -p ~/.config/fish ~/.termux ~/files/btop ~/.local/bin
cp resources/config/fish/config.fish ~/.config/fish/config.fish
cp resources/config/tmux/.tmux.conf ~/.tmux.conf
cp resources/config/termux/termux.properties ~/.termux/termux.properties
cp resources/scripts/restore_btop.sh ~/files/btop/restore_btop.sh
cp resources/scripts/statusbar/kew-now-playing ~/.local/bin/kew-now-playing
cp resources/scripts/statusbar/weather-cache ~/.local/bin/weather-cache
cp resources/bin/termux-restart $PREFIX/bin/termux-restart
chmod +x ~/files/btop/restore_btop.sh ~/.local/bin/kew-now-playing ~/.local/bin/weather-cache $PREFIX/bin/termux-restart
termux-reload-settings
```

### 3) Optional keyboard layout (Unexpected Keyboard)

- Install Unexpected Keyboard:
  https://play.google.com/store/apps/details?id=juloo.keyboard2
- Import/use the included layout file:
  `resources/config/termux/unexp-keyb-layout.txt`

### 4) App settings to configure

- Enable wallpaper sync so system wallpaper and in-app wallpaper stay aligned (Monet works better).
- Set an uncommon input split character, for example `@` or `#`, to trigger Android app search via suggestions bar.
- Set default apps as comma-separated values, for example:
  `phone,whatsapp,chrome`

### 5) Optional dev stack references

If you want the fuller setup I use, see:
- https://github.com/PickleHik3/termux-init

This repo includes quick references from there:

```sh
# Neovim Termux LSP plugin config
mkdir -p ~/.config/nvim/lua/plugins
cp resources/optional/termux-init/nvim/termux.lua ~/.config/nvim/lua/plugins/termux.lua

# Crush config example
mkdir -p ~/.config/crush
cp resources/optional/termux-init/crush/crush.example.json ~/.config/crush/crush.json
```

## Keybinds

### Fish keybinds (`resources/config/fish/config.fish`)

| Key | Action |
| --- | --- |
| `Alt+e` | Send current command to `aichat` (`_aichat_fish`) |

### Tmux keybinds (`resources/config/tmux/.tmux.conf`)

| Key | Action |
| --- | --- |
| `Prefix` = `Ctrl+b` | Main tmux prefix |
| `Alt+1..9` | Jump to window 1..9 |
| `Alt+c` | New window |
| `Alt+x` | Kill current window |
| `Alt+z` | Last active window |
| `Alt+r` | Replace current window (new + kill previous) |
| `Alt+Arrow` | Move between panes |
| `Prefix + |` | Split pane horizontally |
| `Prefix + -` | Split pane vertically |
| `Prefix + R` | Reload tmux config |
| `Ctrl+Space` | Launch BlueLineConsole (Omnisearch) |
| `Alt+w` | Launch WhatsApp |
| `Alt+y` | Launch YouTube |
| `Alt+b` | Launch Brave |
| `Alt+t` | Launch Mihon |
| `Alt+f` | Launch Solid Explorer |
| `Alt+s` | Launch Android Settings |
| `Alt+Space` | Launch BlueLineConsole |

### Termux extra-keys (`resources/config/termux/termux.properties`)

| Extra key | Sends | Result |
| --- | --- | --- |
| `🔥` | `Ctrl+b` `Backspace` | Open btop mini window |
| `🕞` | `Ctrl+b` `Tab` | Open peaclock |
| `🎧` | `Ctrl+b` `Esc` | Open kew |
| `📂` | `Ctrl+b` `Enter` | Open yazi |
| `🌠` | `Ctrl+b` `Del` | Open crush |
| `📒` | `Ctrl+b` `PgUp` | Open nvim |
| `🗓️` | `Ctrl+b` `PgDn` | Open calcure |
| `🔍` | `Ctrl+b` `End` | Launch BlueLineConsole |
| `⇄` | `Alt+z` | Switch to last tmux window |
| `𝍣` | `Ctrl+b` `-` | Split pane vertically |
| `𝍬` (popup) | `Ctrl+b` `|` | Split pane horizontally |
| `⓵` `⓶` `⓷` | `Ctrl+b` `1/2/3` | Switch tmux window |
| `✎` | `Ctrl+b` `[` | Enter copy mode |
| `㋡` | `Ctrl+b` | Prefix helper key |

## Quirks And Limitations

- `tooie-shelf` is experimental and not fully functional in this setup:
  https://github.com/PickleHik3/tooie-shelf
- Install `termux-api` for wallpaper sync-to-system features.
- `btop` needs Shizuku + `rish`, plus generic Linux btop from:
  https://github.com/aristocratos/btop/releases
  Then use `resources/scripts/restore_btop.sh`.
- tmux status-right expects:
  - `~/.local/bin/kew-now-playing`
  - `~/.local/bin/weather-cache`
- `weather-cache` uses `Kuwait` by default; edit the script to change city.
- If shell exit causes stutter, force close app or run `termux-restart`.
- Termux:X11 currently does not work well with this setup.
- Battery usage is untested; on my Nothing Phone 2 it has been mostly fine.
- Pacman/TUR guide:
  https://wiki.termux.com/wiki/Switching_package_manager
- Users are encouraged to build APKs themselves or use nightly workflow artifacts.

## TUI Apps (from `tui.txt`)

- https://github.com/charmbracelet/crush
- https://github.com/am2rican5/sigye
- https://github.com/octobanana/peaclock
- https://github.com/sxyazi/yazi
- https://github.com/ravachol/kew
- https://github.com/anufrievroman/calcure
- https://github.com/antonmedv/walk
- More TUIs: https://terminaltrove.com/

## Screenshots

<table>
  <tr>
    <td><img src="screenshots/home-screen.png" width="360" alt="Home screen"/></td>
    <td><img src="screenshots/home-screen-blur.png" width="360" alt="Home screen blur"/></td>
  </tr>
  <tr>
    <td><img src="screenshots/app-search.png" width="360" alt="App search"/></td>
    <td><img src="screenshots/app-settings.png" width="360" alt="App settings"/></td>
  </tr>
  <tr>
    <td><img src="screenshots/shell-env.png" width="360" alt="Shell environment"/></td>
    <td><img src="screenshots/pacman-termux.png" width="360" alt="Pacman in Termux"/></td>
  </tr>
  <tr>
    <td><img src="screenshots/lazyvim.png" width="360" alt="LazyVim"/></td>
    <td><img src="screenshots/btop-android.png" width="360" alt="btop on Android"/></td>
  </tr>
  <tr>
    <td><img src="screenshots/TUI-crush-ai.png" width="360" alt="Crush AI"/></td>
    <td><img src="screenshots/TUI-sigye-clock.png" width="360" alt="Sigye clock"/></td>
  </tr>
  <tr>
    <td><img src="screenshots/TUI-yazi.png" width="360" alt="Yazi"/></td>
    <td><img src="screenshots/TUI-kew-music.png" width="360" alt="Kew music"/></td>
  </tr>
  <tr>
    <td><img src="screenshots/TUI-calcure.png" width="360" alt="Calcure"/></td>
    <td><img src="screenshots/TUI-tooie-shelf.png" width="360" alt="Tooie shelf"/></td>
  </tr>
</table>
