# --- 1. Environment Variables (Load First) ---
# gle
# Load these first so both the outer shell and Tmux inherit them.
set -gx EDITOR nvim
set -gx TMPDIR $HOME/.tmp
set -gx XDG_CONFIG_HOME $HOME/.config
set -gx XDG_DATA_HOME $HOME/.local/share
set -gx XDG_CACHE_HOME $HOME/.cache

# Load secrets from external file (not tracked in git)
if test -f ~/.config/fish/secrets.fish
    source ~/.config/fish/secrets.fish
end

# Cache GitHub token (only set if not already defined and gh is available)
# if not set -q GITHUB_TOKEN; and type -q gh
#     set -gx GITHUB_TOKEN (gh auth token 2>/dev/null)
# end
# Fix terminal type to prevent control characters from being displayed
# truecolor or sixel support which work through other mechanisms

# Terminal capabilities for TUI apps
set -gx COLORTERM truecolor
set -gx TERMUX_VERSION $TERMUX_VERSION

# Terminal identity for iTerm2 protocol and TUI app detection
set -gx TERM_PROGRAM iTerm.app
set -gx LC_TERMINAL iTerm2

# Only set TERM outside tmux - let tmux control its own TERM
if not set -q TMUX
    set -gx TERM xterm-256color
end

# Fix for some TUI apps that check locale
if not set -q LANG
    set -gx LANG en_US.UTF-8
end

# ANDROID_API_LEVEL for compiling
if not set -q ANDROID_API_LEVEL
    set -gx ANDROID_API_LEVEL 33
end

# NVIDIA NIM API Key for Avante.nvim
set -gx NVIDIA_API_KEY "<set-your-key-here>"
# set -gx OPENAI_API_KEY "<set-your-key-here>"
# Ensure temp dir exists to prevent startup errors
if not test -d $TMPDIR
    mkdir -p $TMPDIR
end

set -g fish_greeting ""

# --- 2. Tmux Auto-Start ---
# Only run if interactive, not inside tmux, and tmux is installed.
if status is-interactive
    and not set -q TMUX
    and type -q tmux # Check if tmux exists to prevent crash loop

    if set -q SSH_CONNECTION
        # SSH session - acquire wake lock and attach to local 'main' session
        termux-wake-lock
        tmux attach -t main
        termux-wake-unlock
        exit
    else
        # Local session
        exec tmux new-session -A -s main
    end
end

# --- 3. Shell Customization (Inner Shell) ---

function clear
    command clear
    # Check if tput exists
    if type -q tput
        set -l lines (tput lines 2>/dev/null)

        # FIX: Use '; and' to combine the two test checks
        if test -n "$lines"; and test "$lines" -gt 5
            set -l padding (math "$lines - 3")
            printf (string repeat -n $padding "\n")
        end
    end
    commandline -f repaint
end

# walker
function lk
    set loc (walk --icons --preview --with-border $argv); and cd $loc
end

# Yazi file manager with directory change on exit
function y
    set tmp (mktemp -t "yazi-cwd.XXXXXX")
    command yazi $argv --cwd-file="$tmp"
    if read -l cwd <"$tmp"; and test "$cwd" != "$PWD"; and test -d "$cwd"
        builtin cd -- "$cwd"
    end
    rm -f -- "$tmp"
end

# Abbreviations
abbr -a cc clear
abbr -a ee exit
abbr -a ii 'pacman -S --needed --noconfirm'
abbr -a ss 'pacman -Ss'
abbr -a rr 'pacman -Rns'
abbr -a uu 'pacman -Syu --needed --noconfirm'
abbr -a cdd 'cd ..'
abbr -a nn nvim
abbr -a fishy 'nvim ~/.config/fish/config.fish'
abbr -a tmuxy 'nvim ~/.tmux.conf'
abbr -a termuxy 'nvim ~/.termux/termux.properties'
abbr -a w which
abbr -a ccfg 'cd ~/.config/'
abbr -a rfish 'exec fish'
abbr -a rtermux termux-reload-settings
abbr -a rtmux 'tmux source-file ~/.tmux.conf'
abbr -a ktmux 'tmux kill-server'
abbr -a mm mkdir
abbr -a py python
abbr -a gitc 'git clone'
abbr -a pd proot-distro
abbr -a peaclock "peaclock --config-dir ~/.config/peaclock"
abbr -a restart termux-restart
abbr -a btop 'rish -c "XDG_CONFIG_HOME=/data/local/tmp/btop-config /data/local/tmp/btop/btop --force-utf"'
abbr -a mini-btop 'rish -c "XDG_CONFIG_HOME=/data/local/tmp/btop-mini /data/local/tmp/btop/btop --force-utf"'

# Aliases (Check if eza exists, fall back to ls if not)
if type -q eza
    alias ls='eza --color=always --group-directories-first --icons'
    alias la='eza --color=always --group-directories-first --icons -a'
    alias ll='eza --color=always --group-directories-first --icons -al'
    alias tree='eza -aT --icons --color=always --group-directories-first'
    alias l.="eza -a | grep -e '^\.'"
else
    alias ls='ls --color=auto'
    alias la='ls -a --color=auto'
    alias ll='ls -al --color=auto'
end

# AI Chat Binding
function _aichat_fish
    set -l _old (commandline)
    if test -n $_old
        echo -n "⌛"
        commandline -f repaint
        commandline (aichat -e $_old)
    end
end
bind \ee _aichat_fish

# Automatic ls on cd
function cd
    builtin cd $argv
    and ls
end

# --- 4. Starship & Zoxide (Final Init) ---
if status --is-interactive
    if type -q starship
        starship init fish | source
        enable_transience

        function starship_transient_prompt_func
            starship module character
        end

        function starship_transient_rprompt_func
            starship module time
        end
    end

    if type -q zoxide
        # Initialize zoxide as 'cd' replacement
        zoxide init fish --cmd cd | source

        # Store zoxide's cd, then wrap it with auto-ls + clear integration
        functions --copy cd _zoxide_cd

        function cd --wraps=_zoxide_cd
            _zoxide_cd $argv
            and ls
        end

        # Quick clear + jump: cdc <dir> clears screen then cd's
        function cdc --wraps=_zoxide_cd --description "Clear screen and cd with zoxide"
            clear
            _zoxide_cd $argv
            and ls
        end
    end

    # Clear screen on startup (normal behavior)
    clear
end

# uv
fish_add_path "/data/data/com.termux/files/home/.local/share/../bin"
