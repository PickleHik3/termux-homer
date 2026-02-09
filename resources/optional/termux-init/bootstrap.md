# Termux Setup Guide

## Table of Contents
1. [Pacman Bootstrap](#1-pacman-bootstrap)
2. [Essential Packages](#2-essential-packages)
3. [Shell Setup (Fish)](#3-shell-setup-fish)
4. [Language Toolchains](#4-language-toolchains)
5. [LSP Servers](#5-lsp-servers)
6. [MCP Servers (Crush TUI)](#6-mcp-servers-crush-tui)
7. [Optional: KEW Music Player](#7-optional-kew-music-player)

---

## 1. Pacman Bootstrap

Replace Termux's default apt with pacman.

```bash
# Setup storage access
termux-setup-storage

# Prepare new usr directory
cd ..
mkdir usr-n/
cp ~/storage/downloads/bootstrap-aarch64.zip usr-n/
cd usr-n/ && unzip bootstrap-aarch64.zip && rm bootstrap-aarch64.zip
cat SYMLINKS.txt | awk -F "←" '{system("ln -s '"'"'"$1"'"'"' '"'"'"$2"'"'"'")}'
```

**⚠️ Restart Termux into Fail Safe mode, then:**

```bash
cd ..
rm -rf usr/
mv usr-n/ usr/
```

**Restart Termux normally.**

---

## 2. Essential Packages

```bash
pacman -Syu wget git fd ripgrep nodejs npm python python-pynvim rust perl \
  neovim-nightly stylua base-devel curl glow yazi tmux fish starship \
  peaclock timg python-pip yq jq termux-api termux-tools aichat which \
  openssh termux-services fzf eza golang gh lazygit diff-so-fancy bat uv
```

### Python packages (from pacman)

```bash
pacman -S --needed --noconfirm uv python-scipy python-numpy \
  python-ensurepip-wheels python-cryptography python-llvmlite \
  python-tkinter python-lxml
```

---

## 3. Shell Setup (Fish)

### Install Fisher plugin manager

```bash
curl -sL https://raw.githubusercontent.com/jorgebucaran/fisher/main/functions/fisher.fish | source && fisher install jorgebucaran/fisher
```

### Install Fish plugins

```bash
fisher install PatrickF1/fzf.fish
fisher install jorgebucaran/autopair.fish
fisher install nickeb96/puffer-fish
fisher install jorgebucaran/getopts.fish
```

### Shell config additions (~/.config/fish/config.fish)

```fish
set -gx ANDROID_API_LEVEL 36
set -gx GITHUB_TOKEN (gh auth token)
```

---

## 4. Language Toolchains

### Python tools

```bash
uv tool install mistral-vibe
pip install pipx
pipx ensurepath
pipx install calcure
```

### Rust tools

```bash
cargo install ast-grep
cargo install --locked tree-sitter-cli
```

### Node.js tools

```bash
npm install -g @mmmbuto/codex-cli-termux
```

### Go tools

```bash
go install golang.org/x/tools/gopls@latest
```

---

## 5. LSP Servers

Install language servers for code intelligence in editors/TUIs.

```bash
npm i -g typescript-language-server typescript pyright \
  bash-language-server yaml-language-server vscode-langservers-extracted
```

| Language | Server | Command |
|----------|--------|---------|
| Go | gopls | `gopls` |
| TypeScript/JS | typescript-language-server | `typescript-language-server --stdio` |
| Python | pyright | `pyright-langserver --stdio` |
| Rust | rust-analyzer | `rust-analyzer` (install via rustup) |
| Bash | bash-language-server | `bash-language-server start` |
| Lua | lua-language-server | `lua-language-server` (if available) |
| YAML | yaml-language-server | `yaml-language-server --stdio` |
| JSON/HTML/CSS | vscode-langservers-extracted | `vscode-json-language-server --stdio` |

---

## 6. MCP Servers (Crush TUI)

Model Context Protocol servers extend AI capabilities in Crush.

### Install dependencies

```bash
npm i -g zod @modelcontextprotocol/server-filesystem \
  @modelcontextprotocol/server-github @modelcontextprotocol/server-memory \
  @modelcontextprotocol/server-brave-search \
  @modelcontextprotocol/server-sequential-thinking
```

### Configuration

MCP servers must be invoked with `node` explicitly on Termux (direct binary execution fails with fork/exec errors).

Config location: `~/.config/crush/crush.json`

```json
{
  "mcp": {
    "filesystem": {
      "type": "stdio",
      "command": "node",
      "args": ["/data/data/com.termux/files/usr/lib/node_modules/@modelcontextprotocol/server-filesystem/dist/index.js", "/data/data/com.termux/files/home"],
      "timeout": 60
    },
    "memory": {
      "type": "stdio",
      "command": "node",
      "args": ["/data/data/com.termux/files/usr/lib/node_modules/@modelcontextprotocol/server-memory/dist/index.js"],
      "timeout": 60
    },
    "github": {
      "type": "stdio",
      "command": "node",
      "args": ["/data/data/com.termux/files/usr/lib/node_modules/@modelcontextprotocol/server-github/dist/index.js"],
      "timeout": 60,
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "$GITHUB_TOKEN"
      }
    },
    "brave-search": {
      "type": "stdio",
      "command": "node",
      "args": ["/data/data/com.termux/files/usr/lib/node_modules/@modelcontextprotocol/server-brave-search/dist/index.js"],
      "timeout": 60,
      "env": {
        "BRAVE_API_KEY": "<your-api-key>"
      }
    },
    "sequential-thinking": {
      "type": "stdio",
      "command": "node",
      "args": ["/data/data/com.termux/files/usr/lib/node_modules/@modelcontextprotocol/server-sequential-thinking/dist/index.js"],
      "timeout": 60
    },
    "fetch": {
      "type": "stdio",
      "command": "uvx",
      "args": ["mcp-server-fetch"],
      "timeout": 120
    }
  }
}
```

### Testing MCP servers manually

```bash
# Each should output "running on stdio" and wait for input (Ctrl+C to exit)
node /data/data/com.termux/files/usr/lib/node_modules/@modelcontextprotocol/server-memory/dist/index.js
node /data/data/com.termux/files/usr/lib/node_modules/@modelcontextprotocol/server-filesystem/dist/index.js ~
uvx mcp-server-fetch --help
```

### Available MCP Servers

| Server | Purpose |
|--------|---------|
| filesystem | Read/write files in allowed directories |
| memory | Persistent knowledge graph for context |
| github | GitHub API integration (requires GITHUB_TOKEN) |
| brave-search | Web search via Brave API |
| sequential-thinking | Chain-of-thought reasoning |
| fetch | Fetch web content (Python, via uvx) |

---

## 7. Optional: KEW Music Player

Terminal-based music player with visualizations.

### Dependencies

```bash
pacman -S --needed --noconfirm clang cmake pkg-config taglib fftw git \
  make chafa glib libopus opusfile libvorbis libogg dbus termux-api
```

### Build faad2

```bash
git clone https://github.com/knik0/faad2
cd faad2
cmake -DCMAKE_EXE_LINKER_FLAGS="-lm" . -D CMAKE_INSTALL_PREFIX=/data/data/com.termux/files/usr
make install
cd ..
```

### Build KEW

```bash
git clone https://codeberg.org/ravachol/kew.git
cd kew
make -j4
make install
cd ..
```

### Fish alias (requires dbus)

```fish
alias kew "dbus-launch kew"
funcsave kew
```
