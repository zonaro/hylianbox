#!/usr/bin/env bash
# HylianBox — Release & Deploy via ADB
#
# Runs the project's release.sh (which builds the signed/unsigned release APK
# and publishes the GitHub release) and then installs the resulting APK on a
# connected device/emulator via adb. Optionally launches the app afterwards.
#
# Usage:
#   ./tools/deploy_release.sh            # release + install + launch
#   LAUNCH=0 ./tools/deploy_release.sh   # release + install only
#   ADB=/path/to/adb ./tools/deploy_release.sh
#
# Notes:
#   - The build logic lives in release.sh at the repo root; this script only
#     adds the ADB install + launch step on top of it (no duplication). By
#     default release.sh ONLY builds the APK — no GitHub interaction happens
#     here, so gh/auth are not required for a local deploy.
#   - To also tag, push and publish the GitHub release, run release.sh --github
#     (or the "Release on GitHub" VS Code task) separately.
#   - A signed APK requires keystore.properties at the repo root (gitignored).
#     Without it, AGP emits an unsigned APK (app-release-unsigned.apk) which
#     adb install will reject on a production device.
#   - Set ANDROID_SERIAL to target a specific device when several are connected.

set -euo pipefail

# ---- Config ----------------------------------------------------------------
PACKAGE="br.com.redclaw.hylianbox"
ADB="${ADB:-adb}"
LAUNCH="${LAUNCH:-1}"            # set LAUNCH=0 to skip launching after install

# Resolve repo root FIRST, then use absolute paths everywhere so this script
# works regardless of the caller's cwd (e.g. VS Code tasks).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"
RELEASE_SCRIPT="$REPO_ROOT/release.sh"
APK_DIR="$REPO_ROOT/app/build/outputs/apk/release"

# ---- Android/Java environment fallback --------------------------------------
# VS Code task environments may lack ANDROID_HOME/JAVA_HOME that interactive
# shells have. Derive the SDK from local.properties or ~/Android/Sdk and
# prefer a Java 17 runtime (required by AGP) when available.
setup_android_env() {
    if [[ -z "${ANDROID_HOME:-}" || -z "${ANDROID_SDK_ROOT:-}" ]]; then
        local sdk_from_props=""
        if [[ -f "$REPO_ROOT/local.properties" ]]; then
            sdk_from_props=$(grep -E '^sdk\.dir=' "$REPO_ROOT/local.properties" | head -n1 | cut -d'=' -f2-)
        fi
        local fallback_sdk="${sdk_from_props:-$HOME/Android/Sdk}"
        if [[ -d "$fallback_sdk" ]]; then
            export ANDROID_HOME="${ANDROID_HOME:-$fallback_sdk}"
            export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$fallback_sdk}"
            export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
        fi
    fi
    # If adb is still not on PATH, try the SDK platform-tools directly.
    if ! command -v "$ADB" >/dev/null 2>&1 && [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
        ADB="$ANDROID_HOME/platform-tools/adb"
    fi
    if ! java -version 2>&1 | grep -q 'version "17'; then
        for candidate in /usr/lib/jvm/*17* /usr/lib/jvm/default; do
            if [[ -x "$candidate/bin/java" ]]; then
                export JAVA_HOME="$candidate"
                export PATH="$JAVA_HOME/bin:$PATH"
                break
            fi
        done
        if ! java -version 2>&1 | grep -q 'version "17'; then
            log_warn "Java 17 not detected (java: $(java -version 2>&1 | head -n1)). AGP requires Java 17+."
        fi
    fi
    log_info "cwd=$(pwd) ANDROID_HOME=${ANDROID_HOME:-<unset>} java=$(java -version 2>&1 | head -n1)"
}
# NOTE: invoked after the log_* helpers are defined (see below).

# ---- Colors (disabled when not a TTY) --------------------------------------
if [[ -t 1 ]]; then
    RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
else
    RED=''; GREEN=''; YELLOW=''; BLUE=''; NC=''
fi

log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

setup_android_env

# ---- Steps -----------------------------------------------------------------
check_device() {
    if ! command -v "$ADB" >/dev/null 2>&1; then
        log_error "adb not found in PATH. Install Android platform-tools or set ADB=/path/to/adb."
        exit 1
    fi

    local devices
    devices=$("$ADB" devices 2>/dev/null | awk 'NR>1 && $1!="" {print $1}')
    local count
    count=$(printf '%s\n' "$devices" | grep -c .)

    if [[ "$count" -eq 0 ]]; then
        log_error "No ADB devices/emulators connected. Connect a device or start an emulator."
        exit 1
    fi

    if [[ "$count" -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
        local first
        first=$(printf '%s\n' "$devices" | head -n1)
        log_warn "Multiple devices found; using the first one: $first (set ANDROID_SERIAL to override)."
        export ANDROID_SERIAL="$first"
    else
        log_ok "Device ready: $("$ADB" get-serialno 2>/dev/null)"
    fi
}

# The actual build + GitHub release is handled by release.sh at the repo root.
run_release() {
    if [[ ! -f "$RELEASE_SCRIPT" ]]; then
        log_error "release.sh not found at $RELEASE_SCRIPT"
        exit 1
    fi
    log_info "Running release.sh (build + GitHub release)..."
    "$RELEASE_SCRIPT"
    log_ok "release.sh finished."
}

find_apk() {
    local apk
    # Prefer signed APKs over -unsigned ones when both exist.
    apk=$(find "$APK_DIR" -name "*.apk" 2>/dev/null | grep -v -- "-unsigned" | head -n1)
    if [[ -z "$apk" ]]; then
        apk=$(find "$APK_DIR" -name "*.apk" 2>/dev/null | head -n1)
    fi
    if [[ -z "$apk" ]]; then
        log_error "No APK found in $APK_DIR. Did the build succeed? Check the Gradle output above for the real cause."
        exit 1
    fi
    echo "$apk"
}

install_apk() {
    local apk="$1"
    if [[ "$apk" == *"-unsigned"* ]]; then
        log_warn "APK is UNSIGNED ($apk). adb install will fail on production devices."
        log_warn "Create keystore.properties at the repo root to produce a signed APK."
    fi
    # ANDROID_SERIAL (exported by check_device or the caller) applies to every
    # adb invocation below: install, launch and verification.
    log_info "Installing $apk (device: ${ANDROID_SERIAL:-default}) ..."
    if ! "$ADB" install -r "$apk"; then
        log_error "adb install failed for $apk on device ${ANDROID_SERIAL:-default}. Run 'adb devices' and check signing (unsigned APKs are rejected)."
        exit 1
    fi
    log_ok "Installed."
}

launch_app() {
    log_info "Launching $PACKAGE ..."
    "$ADB" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || \
        log_warn "Could not auto-launch $PACKAGE (monkey returned non-zero)."
    log_ok "Launch intent sent."
}

main() {
    echo "=================================================="
    echo "  HylianBox — Build & Deploy (ADB)"
    echo "=================================================="
    echo
    check_device
    run_release
    local apk
    apk=$(find_apk)
    install_apk "$apk"
    if [[ "${LAUNCH}" != "0" ]]; then
        launch_app
    fi
    echo
    log_ok "Deploy complete: $apk"
}

main "$@"
