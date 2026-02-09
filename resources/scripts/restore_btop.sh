#!/data/data/com.termux/files/usr/bin/bash

# --- Configuration ---
LOCAL_BTOP="$HOME/files/btop/btop"
ANDROID_DIR="/data/local/tmp/btop"

# Config 1 Path (Standard)
CONFIG_STD="/data/local/tmp/btop-config/btop"
# Config 2 Path (Mini)
CONFIG_MINI="/data/local/tmp/btop-mini/btop"

echo "[-] Starting btop setup..."

# 1. Validation
if [ ! -f "$LOCAL_BTOP" ]; then
  echo "ERROR: Binary not found at $LOCAL_BTOP"
  exit 1
fi

# 2. Push Binary (Common for both)
echo "[-] Pushing btop binary to Android..."
rish -c "mkdir -p $ANDROID_DIR"
cat "$LOCAL_BTOP" | rish -c "cat > $ANDROID_DIR/btop"
rish -c "chmod 755 $ANDROID_DIR/btop"

# ==========================================
# CONFIG 1: Standard (CPU Top | Mem & Proc Split)
# ==========================================
echo "[-] Writing Standard Configuration..."
rish -c "mkdir -p $CONFIG_STD"
cat <<EOF | rish -c "cat > $CONFIG_STD/btop.conf"
# --- LAYOUT ---
# CPU on Top, Mem & Proc below (side-by-side)
shown_boxes = "cpu mem proc"
 
# --- SAFETY (No Crashes) ---
io_mode = False
show_io_stat = False
net_auto = False
net_sync = False
net_iface = ""
show_disks = False
 
# --- SETTINGS ---
color_theme = "TTY"
theme_background = False
truecolor = True
rounded_corners = True
graph_symbol = "braille"
cpu_graph_upper = "total"
cpu_graph_lower = "total"
proc_sorting = "cpu lazy"
proc_tree = False
update_ms = 2000
EOF

# ==========================================
# CONFIG 2: Mini (CPU Top | Proc Below)
# ==========================================
echo "[-] Writing Mini Configuration..."
rish -c "mkdir -p $CONFIG_MINI"
cat <<EOF | rish -c "cat > $CONFIG_MINI/btop.conf"
# --- LAYOUT ---
# Only CPU and Process List
shown_boxes = "cpu proc"
 
# --- SAFETY ---
io_mode = False
show_io_stat = False
net_auto = False
net_sync = False
net_iface = ""
show_disks = False
 
# --- SETTINGS ---
color_theme = "TTY"
theme_background = False
truecolor = True
rounded_corners = True
graph_symbol = "braille"
cpu_single_graph = True
cpu_graph_upper = "total"
cpu_graph_lower = "total"
proc_sorting = "cpu lazy"
proc_tree = False
update_ms = 2000
EOF

echo "[+] Setup Complete!"
echo "    Run 'btop' for Standard View (CPU | Mem+Proc)"
echo "    Run 'mini-btop'    for Mini View     (CPU | Proc)"
