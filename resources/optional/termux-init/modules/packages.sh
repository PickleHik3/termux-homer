#!/data/data/com.termux/files/usr/bin/bash
# Module: Essential Packages

install_essential_packages() {
    header "Installing Essential Packages"

    # Detect package manager
    local pkg_cmd=""
    if command -v pacman &>/dev/null; then
        pkg_cmd="pacman -S --needed --noconfirm"
        log "Using pacman"
    elif command -v pkg &>/dev/null; then
        pkg_cmd="pkg install -y"
        log "Using pkg (apt)"
    else
        error "No package manager found!"
        return 1
    fi

    # Core packages
    local core_packages=(
        wget git fd ripgrep nodejs python rust perl
        neovim stylua base-devel curl glow yazi tmux fish starship
        peaclock timg python-pip yq jq termux-api termux-tools which
        openssh termux-services fzf eza golang gh lazygit bat uv
    )

    log "Installing core packages..."
    $pkg_cmd "${core_packages[@]}" || warn "Some packages may have failed"

    # Python packages (pacman only)
    if [[ "$pkg_cmd" == *"pacman"* ]]; then
        local python_packages=(
            python-scipy python-numpy python-ensurepip-wheels
            python-cryptography python-llvmlite python-tkinter python-lxml
        )

        log "Installing Python packages..."
        pacman -S --needed --noconfirm "${python_packages[@]}" || warn "Some Python packages may have failed"
    fi

    # Setup storage access
    if [[ ! -d ~/storage ]]; then
        log "Setting up storage access..."
        termux-setup-storage || warn "Storage setup may require manual confirmation"
    fi

    log "Essential packages installed."
}
