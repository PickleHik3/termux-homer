-- Termux-specific LSP configuration
-- All servers point to local Termux pacman/npm installations
-- This file ensures easy backup/restore without relying on Mason

local lsp_bin = "/data/data/com.termux/files/usr/bin"

return {
  -- Configure conform.nvim to use system stylua
  {
    "stevearc/conform.nvim",
    opts = {
      formatters = {
        stylua = {
          command = lsp_bin .. "/stylua",
        },
      },
    },
  },

  -- Configure ALL LSP servers to use local installations
  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        -- Lua
        lua_ls = {
          cmd = { lsp_bin .. "/lua-language-server" },
        },

        -- Python
        pyright = {
          cmd = { lsp_bin .. "/pyright" },
        },

        -- TypeScript/JavaScript
        ts_ls = {
          cmd = { lsp_bin .. "/typescript-language-server", "--stdio" },
        },

        -- Go
        gopls = {
          cmd = { lsp_bin .. "/gopls" },
        },

        -- Rust
        rust_analyzer = {
          cmd = { lsp_bin .. "/rust-analyzer" },
        },

        -- C/C++
        clangd = {
          cmd = { lsp_bin .. "/clangd" },
        },

        -- HTML
        html = {
          cmd = { "/data/data/com.termux/files/usr/lib/node_modules/vscode-langservers-extracted/bin/vscode-html-language-server", "--stdio" },
        },

        -- CSS
        cssls = {
          cmd = { "/data/data/com.termux/files/usr/lib/node_modules/vscode-langservers-extracted/bin/vscode-css-language-server", "--stdio" },
        },

        -- JSON
        jsonls = {
          cmd = { "/data/data/com.termux/files/usr/lib/node_modules/vscode-langservers-extracted/bin/vscode-json-language-server", "--stdio" },
        },

        -- YAML
        yamlls = {
          cmd = { lsp_bin .. "/yaml-language-server", "--stdio" },
        },

        -- Bash
        bashls = {
          cmd = { lsp_bin .. "/bash-language-server", "start" },
        },

        -- Markdown
        marksman = {
          cmd = { lsp_bin .. "/marksman", "server" },
        },

        -- TOML
        taplo = {
          cmd = { lsp_bin .. "/taplo", "lsp", "stdio" },
        },

        -- Assembly
        asm_lsp = {
          cmd = { lsp_bin .. "/asm-lsp" },
        },
      },
    },
  },

  -- Disable mason-lspconfig (depends on Mason which we don't use)
  {
    "mason-org/mason-lspconfig.nvim",
    enabled = false,
  },

  -- Disable Mason entirely (we use pacman/npm instead)
  {
    "mason-org/mason.nvim",
    enabled = false,
  },
}
