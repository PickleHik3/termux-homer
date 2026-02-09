# Termux Homer (Frankenstein Launcher)

This repo is a **proof of concept** I vibecoded for my personal use.

It is a fork/mashup of:
- Termux + Termux Monet ideas
- TEL launcher ideas

Goal: make a modern Android launcher experience that is basically just **Termux**.

I do **not** want to take credit for the original work. This is just my personal experiment, and I would love for a real developer to take this idea further.

## Credits

All major credit goes upstream:
- Termux app: https://github.com/termux/termux-app
- Termux Monet: https://github.com/Termux-Monet/termux-monet
- TEL project/wiki: https://github.com/t-e-l/wiki

## Screenshots

All screenshots are in `screenshots/`.

### Launcher / app flow
- `screenshots/home-screen.png`

  ![Home screen](screenshots/home-screen.png)

- `screenshots/home-screen-blur.png`

  ![Home screen blur](screenshots/home-screen-blur.png)

- `screenshots/app-search.png`

  ![App search](screenshots/app-search.png)

- `screenshots/app-settings.png`

  ![App settings](screenshots/app-settings.png)

### Shell + tools
- `screenshots/shell-env.png`

  ![Shell environment](screenshots/shell-env.png)

- `screenshots/pacman-termux.png`

  ![Pacman in Termux](screenshots/pacman-termux.png)

- `screenshots/lazyvim.png`

  ![LazyVim](screenshots/lazyvim.png)

- `screenshots/btop-android.png`

  ![btop on Android](screenshots/btop-android.png)

### TUI apps
- `screenshots/TUI-crush-ai.png`

  ![Crush AI](screenshots/TUI-crush-ai.png)

- `screenshots/TUI-sigye-clock.png`

  ![Sigye clock](screenshots/TUI-sigye-clock.png)

- `screenshots/TUI-yazi.png`

  ![Yazi](screenshots/TUI-yazi.png)

- `screenshots/TUI-kew-music.png`

  ![Kew music](screenshots/TUI-kew-music.png)

- `screenshots/TUI-calcure.png`

  ![Calcure](screenshots/TUI-calcure.png)

- `screenshots/TUI-tooie-shelf.png`

  ![Tooie shelf](screenshots/TUI-tooie-shelf.png)

## TUI apps currently installed

From `tui.txt`:
- https://github.com/charmbracelet/crush
- https://github.com/am2rican5/sigye
- https://github.com/octobanana/peaclock
- https://github.com/sxyazi/yazi
- https://github.com/ravachol/kew
- https://github.com/anufrievroman/calcure
- https://github.com/antonmedv/walk

More TUIs: https://terminaltrove.com/

## Included files (download/use directly)

Everything below is now included in this repo under `resources/`:

| Type | Path |
| --- | --- |
| Fish config | `resources/config/fish/config.fish` |
| Tmux config | `resources/config/tmux/.tmux.conf` |
| Termux properties | `resources/config/termux/termux.properties` |
| btop restore script | `resources/scripts/restore_btop.sh` |
| tmux status script (kew ticker) | `resources/scripts/statusbar/kew-now-playing` |
| tmux status script (weather ticker) | `resources/scripts/statusbar/weather-cache` |
| `termux-restart` helper | `resources/bin/termux-restart` |

Quick setup example:

```sh
cp resources/config/fish/config.fish ~/.config/fish/config.fish
cp resources/config/tmux/.tmux.conf ~/.tmux.conf
cp resources/config/termux/termux.properties ~/.termux/termux.properties
cp resources/scripts/restore_btop.sh ~/files/btop/restore_btop.sh
mkdir -p ~/.local/bin
cp resources/scripts/statusbar/kew-now-playing ~/.local/bin/kew-now-playing
cp resources/scripts/statusbar/weather-cache ~/.local/bin/weather-cache
cp resources/bin/termux-restart $PREFIX/bin/termux-restart
chmod +x ~/files/btop/restore_btop.sh $PREFIX/bin/termux-restart ~/.local/bin/kew-now-playing ~/.local/bin/weather-cache
```

## Easy setup (for non-terminal users)

Follow this exactly in Termux:

1. Install required packages:

```sh
pkg update && pkg upgrade -y
pkg install -y fish tmux termux-api git curl
```

2. Copy included configs from this repo:

```sh
mkdir -p ~/.config/fish ~/.termux ~/files/btop
cp resources/config/fish/config.fish ~/.config/fish/config.fish
cp resources/config/tmux/.tmux.conf ~/.tmux.conf
cp resources/config/termux/termux.properties ~/.termux/termux.properties
cp resources/scripts/restore_btop.sh ~/files/btop/restore_btop.sh
mkdir -p ~/.local/bin
cp resources/scripts/statusbar/kew-now-playing ~/.local/bin/kew-now-playing
cp resources/scripts/statusbar/weather-cache ~/.local/bin/weather-cache
cp resources/bin/termux-restart $PREFIX/bin/termux-restart
chmod +x ~/files/btop/restore_btop.sh $PREFIX/bin/termux-restart ~/.local/bin/kew-now-playing ~/.local/bin/weather-cache
termux-reload-settings
```

3. Make Fish your default shell:

```sh
chsh -s fish
```

4. Restart Termux completely:
- Close the app from recent apps and open it again.
- If it stutters, run `termux-restart`.

5. In app settings:
- Enable wallpaper sync option if you want Monet colors to apply correctly.
- Set an uncommon input split character (like `@` or `#`) for better app-search behavior.
- Set default apps as comma-separated values (example: `phone,whatsapp,chrome`).

6. Optional but recommended:
- Install Unexpected Keyboard:
  https://play.google.com/store/apps/details?id=juloo.keyboard2
- For Android app launching, use BlueLineConsole:
  https://github.com/nhirokinet/bluelineconsole
- `tooie-shelf` is experimental; track it upstream:
  https://github.com/PickleHik3/tooie-shelf

## Keyboard + keybind setup

### Fish keybinds (`$HOME/.config/fish/config.fish`)

| Key | Action |
| --- | --- |
| `Alt+e` | Send current command to `aichat` (`_aichat_fish`) |

### Tmux keybinds (`$HOME/.tmux.conf`)

| Key | Action |
| --- | --- |
| `Prefix` = `Ctrl+b` | Main tmux prefix |
| `Alt+1..9` | Jump to window 1..9 |
| `Alt+c` | New window |
| `Alt+x` | Kill current window |
| `Alt+z` | Last active window |
| `Alt+r` | Replace current window (new + kill previous) |
| `Alt+Arrow` | Move between panes |
| `Ctrl+Shift+Arrow` | Resize pane |
| `Prefix + |` | Split pane horizontally |
| `Prefix + -` | Split pane vertically |
| `Prefix + R` | Reload tmux config |
| `Ctrl+Space` | Open app launcher (`tooie-shelf`) |
| `Alt+w` | Launch WhatsApp |
| `Alt+y` | Launch YouTube |
| `Alt+b` | Launch Brave |
| `Alt+t` | Launch Mihon |
| `Alt+f` | Launch Solid Explorer |
| `Alt+s` | Launch Android Settings |
| `Alt+Space` | Launch Blue Line Console |

### Termux extra-keys (`$HOME/.termux/termux.properties`)

Visible in screenshots; configured as two rows.

| Extra key | Sends | Result |
| --- | --- | --- |
| `🔥` | `Ctrl+b` `Backspace` | Open btop mini window |
| `🕞` | `Ctrl+b` `Tab` | Open peaclock |
| `🎧` | `Ctrl+b` `Esc` | Open kew |
| `📂` | `Ctrl+b` `Enter` | Open yazi |
| `🌠` | `Ctrl+b` `Del` | Open crush |
| `📒` | `Ctrl+b` `PgUp` | Open nvim |
| `🗓️` | `Ctrl+b` `PgDn` | Open calcure |
| `🔍` | `Ctrl+b` `End` | Launch Blue Line Console |
| `⇄` | `Alt+z` | Switch to last tmux window |
| `𝍣` | `Ctrl+b` `-` | Split pane vertically |
| `𝍬` (popup) | `Ctrl+b` `|` | Split pane horizontally |
| `⓵` `⓶` `⓷` | `Ctrl+b` `1/2/3` | Switch tmux window |
| `✎` | `Ctrl+b` `[` | Enter copy mode |
| `㋡` | `Ctrl+b` | Prefix helper key |

## Recommended keyboard

For this style of launcher, I strongly recommend **Unexpected Keyboard**:
- https://play.google.com/store/apps/details?id=juloo.keyboard2

## Quirks / important notes

- Set an uncommon **input split character** in app settings (for example `@` or `#`).
  This makes it easier to trigger Android app search via the suggestions bar.
- Default apps can be configured as comma-separated values, for example: `phone,whatsapp,chrome`.
- `tooie-shelf` is experimental and currently not fully functional in this setup.
  Use the upstream repo directly: https://github.com/PickleHik3/tooie-shelf
- Enable **Sync system wallpaper** in settings so in-app wallpaper and system wallpaper stay aligned.
  This helps Monet colors apply properly.
- Install the `termux-api` package if you want wallpaper sync-to-system to work.
- For easier Android app launching, I recommend:
  https://github.com/nhirokinet/bluelineconsole
- Alternative launcher panel:
  https://play.google.com/store/apps/details?id=com.fossor.panels
- `btop` setup requires Shizuku + `rish`: set up Shizuku first, place `rish` in `~/.rish`, download generic Linux btop from
  https://github.com/aristocratos/btop/releases, then use `resources/scripts/restore_btop.sh` and make files executable.
- A custom `termux-restart` helper is provided at `resources/bin/termux-restart`.
  Copy it to `$PREFIX/bin/termux-restart` and make it executable.
  It uses `rish` capability to force-stop and restart the app.
- tmux status-right uses two local scripts:
  `~/.local/bin/kew-now-playing` and `~/.local/bin/weather-cache`.
  You can copy them from `resources/scripts/statusbar/`.
- `weather-cache` uses a fixed location (`Kuwait`) by default.
  Edit `~/.local/bin/weather-cache` if you want a different city.
- If you exit the shell with normal `exit`, the app can become stuttery.
  If that happens, force-close from Android settings or run `termux-restart`.
- Battery usage is untested. On my device (Nothing Phone 2, around 3 years old), it has mostly been fine.
- Termux:X11 currently does not seem to work with this setup.
- I recommend using the Pacman package manager to access TUR packages more easily (including Python-related packages):
  - https://wiki.termux.com/wiki/Switching_package_manager
- Users are encouraged to build APKs themselves, or use artifacts from the nightly workflow.

## Notes

This is not polished and not production-ready.

It is a personal vibecoded concept that proves the UX can work. If you are an actual Android/launcher developer and want to build this properly, please do.
