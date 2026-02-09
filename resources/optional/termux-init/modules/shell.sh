#!/data/data/com.termux/files/usr/bin/bash
# Module: Fish Shell + Plugins

install_fish_shell() {
    header "Setting up Fish Shell"

    # Ensure fish is installed
    if ! command -v fish &>/dev/null; then
        error "Fish shell not installed. Install essential packages first."
        return 1
    fi

    # Set fish as default shell
    if [[ ! -L "$TERMUX_DIR/shell" ]] || [[ "$(readlink "$TERMUX_DIR/shell")" != *"fish"* ]]; then
        log "Setting fish as default shell..."
        ln -sf "$(which fish)" "$TERMUX_DIR/shell"
    fi

    # Create fish config directories
    mkdir -p "$HOME/.config/fish/functions"
    mkdir -p "$HOME/.config/fish/completions"
    mkdir -p "$HOME/.config/fish/conf.d"

    # Install Fisher plugin manager
    log "Installing Fisher plugin manager..."
    if [[ ! -f "$HOME/.config/fish/functions/fisher.fish" ]]; then
        fish -c 'curl -sL https://raw.githubusercontent.com/jorgebucaran/fisher/main/functions/fisher.fish | source && fisher install jorgebucaran/fisher'
        log "Fisher installed successfully"
    else
        log "Fisher already installed, updating..."
        fish -c 'fisher update jorgebucaran/fisher' 2>/dev/null || true
    fi

    # Install Fish plugins
    local plugins=(
        "PatrickF1/fzf.fish"
        "jorgebucaran/autopair.fish"
        "nickeb96/puffer-fish"
        "jorgebucaran/getopts.fish"
    )

    log "Installing Fish plugins..."
    for plugin in "${plugins[@]}"; do
        log "  Installing $plugin..."
        fish -c "fisher install $plugin" 2>/dev/null || warn "  Plugin $plugin may already be installed"
    done

    # Install zoxide
    if ! command -v zoxide &>/dev/null; then
        log "Installing zoxide..."
        if command -v pacman &>/dev/null; then
            pacman -S --needed --noconfirm zoxide
        elif command -v cargo &>/dev/null; then
            cargo install zoxide --locked
        fi
    else
        log "Zoxide already installed"
    fi

    log "Fish shell setup complete."
    info "Restart terminal or run 'exec fish' to use fish shell"
}
