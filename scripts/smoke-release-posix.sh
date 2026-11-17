#!/usr/bin/env bash
# Native smoke must work with the packaged runtime alone, without Kotlin or system Java.
set -euo pipefail

# Keep failures visible to CI; callers return immediately so the EXIT trap owns cleanup.
smoke_fail() { printf '%s\n' "$*" >&2; return 1; }

# Finder and application shutdown can briefly keep a mounted DMG busy. Retry only
# that documented transient condition, with five attempts and bounded backoff.
# Other errors usually identify a wrong device or permissions and must fail at once.
smoke_detach() {
    local device=$1 attempt=0 output status
    while :; do
        if output=$(hdiutil detach "$device" -force 2>&1); then return 0; else status=$?; fi
        if [[ $output != *"Resource busy"* || $attempt -ge 4 ]]; then
            printf '%s\n' "$output" >&2
            return "$status"
        fi
        sleep "$((1 << attempt))"
        attempt=$((attempt + 1))
    done
}

# Inspect process groups using the shared Linux/macOS ps output format.
smoke_group_alive() {
    local pid=$1 processes
    # A container's PID 1 may retain orphaned zombies. They have already stopped
    # and cannot respond to signals, so kill -0 alone cannot prove liveness.
    # Capture ps separately so a failed process inventory cannot look like success.
    processes=$(ps -axo pgid=,stat=) || return 2
    awk -v group="$pid" '$1 == group && $2 !~ /^Z/ { live = 1 } END { exit !live }' <<< "$processes"
}

# Every background job is started with job control enabled below. Its PID is also
# its process-group ID, so TERM reaches the launcher and its inherited children.
# Only PIDs captured by this invocation may be passed to this function.
# Wait only for running descendants; wait below still validates our own child's
# exit status and reaps it even when no live members remain in its process group.
smoke_stop() {
    local pid=$1 label=$2 deadline status=0 group_status
    # A launcher may exit before its children. Inspect and terminate its entire
    # owned group even when the original PID has already disappeared.
    if kill -0 -- "-$pid" 2>/dev/null; then
        kill -TERM -- "-$pid" 2>/dev/null || true
        deadline=$((SECONDS + 30))
        while :; do
            if smoke_group_alive "$pid"; then group_status=0; else group_status=$?; fi
            if ((group_status == 1)); then break; fi
            # Inventory errors fail closed and use the same bounded escalation as
            # a live descendant. Never report a successful shutdown without proof.
            if ((group_status > 1 || SECONDS >= deadline)); then break; fi
            sleep 0.25
        done
        if ((group_status != 1)); then
            kill -KILL -- "-$pid" 2>/dev/null || true
            wait "$pid" 2>/dev/null || true
            smoke_fail "$label did not stop after SIGTERM"
            return 1
        fi
    fi
    wait "$pid" 2>/dev/null || status=$?
    if [[ $status != 0 && $status != 143 ]]; then smoke_fail "$label shutdown returned $status"; fi
}

# Save the original result before running cleanup commands. A cleanup failure must
# turn success into failure, while an earlier startup error keeps its exit status.
# Run every cleanup stage even if another stage fails: leaked mounts or installed
# packages would affect later matrix jobs and conceal isolation regressions.
smoke_cleanup() {
    local status=$? cleanup_status=0 log
    trap - EXIT INT TERM
    if [[ -n ${smoke_app_pid:-} ]]; then smoke_stop "$smoke_app_pid" Application || cleanup_status=1; fi
    if [[ -n ${smoke_display_pid:-} ]]; then smoke_stop "$smoke_display_pid" Xvfb || cleanup_status=1; fi
    if [[ ${smoke_installed:-} == deb ]]; then apt-get remove --yes visual-agent || cleanup_status=1; fi
    if [[ ${smoke_installed:-} == rpm ]]; then dnf remove --assumeyes visual-agent || cleanup_status=1; fi
    if [[ -n ${smoke_device:-} ]]; then smoke_detach "$smoke_device" || cleanup_status=1; fi
    # Publish bounded diagnostics before deleting this invocation's scratch data.
    if ((status != 0 || cleanup_status != 0)); then
        for log in application.log spring.log virtual-display.log; do
            if [[ -f $smoke_work/$log ]]; then tail -c 32768 "$smoke_work/$log" >&2; fi
        done
    fi
    # This uniquely created directory belongs exclusively to this invocation.
    cd "$smoke_repository"
    if [[ -d $smoke_work && $smoke_work == "$smoke_repository"/build/visual-agent-smoke.* ]]; then
        find "$smoke_work" -depth -delete || cleanup_status=1
    fi
    if ((status == 0)); then status=$cleanup_status; fi
    exit "$status"
}

# Native package paths come from the package manager's installed-file inventory.
# Never guess an installation prefix: both missing and ambiguous launchers fail.
# Mark installation before invoking the manager so partially failed installs also
# get an uninstall attempt from the EXIT trap. JAR and AppImage need no install.
smoke_linux_command() {
    local kind=$1 artifact=$2 inventory path
    case $kind in
        jar) smoke_command=(java -jar "$artifact"); return ;;
        appimage) chmod +x "$artifact"; smoke_command=("$artifact"); return ;;
        deb)
            smoke_installed=deb
            apt-get install --yes "$artifact"
            inventory=$(dpkg-query --listfiles visual-agent)
            ;;
        rpm)
            smoke_installed=rpm
            dnf install --assumeyes "$artifact"
            inventory=$(rpm --query --list visual-agent)
            ;;
        *) smoke_fail "Unsupported Linux artifact kind: $kind"; return 1 ;;
    esac
    smoke_command=()
    while IFS= read -r path; do
        if [[ $path == */bin/Visual\ Agent ]]; then smoke_command+=("$path"); fi
    done <<< "$inventory"
    [[ ${#smoke_command[@]} == 1 ]] || smoke_fail "Expected one installed launcher in package metadata, found ${#smoke_command[@]}"
}

# Mount the downloaded DMG read-only and retain its device identity immediately.
# The EXIT trap can then detach it even if bundle/executable discovery fails.
# Invoke the real bundle executable directly so its PID and output remain owned.
smoke_macos_command() {
    local kind=$1 artifact=$2 attached line path
    if [[ $kind == jar ]]; then smoke_command=(java -jar "$artifact"); return; fi
    [[ $kind == dmg ]] || { smoke_fail "Unsupported macOS artifact kind: $kind"; return 1; }
    mkdir "$smoke_work/mounted"
    attached=$(hdiutil attach -nobrowse -readonly -mountpoint "$smoke_work/mounted" "$artifact")
    while IFS= read -r line; do
        if [[ $line == /dev/disk* ]]; then smoke_device=${line%%[[:space:]]*}; break; fi
    done <<< "$attached"
    [[ -n $smoke_device ]] || { smoke_fail "Could not identify the mounted disk image device"; return 1; }
    smoke_command=()
    shopt -s nullglob
    local bundles=("$smoke_work/mounted/"*.app)
    [[ ${#bundles[@]} -gt 0 ]] || { smoke_fail "The DMG did not contain a .app bundle"; return 1; }
    for path in "${bundles[0]}/Contents/MacOS/"*; do
        if [[ -f $path && -x $path ]]; then smoke_command+=("$path"); fi
    done
    [[ ${#smoke_command[@]} == 1 ]] || smoke_fail "Expected one app executable, found ${#smoke_command[@]}"
}

# Let Xvfb allocate a free display and publish its number through descriptor 3.
# A running process alone is insufficient evidence that the X server is ready.
# The unique output file prevents stale display numbers from previous invocations.
smoke_display() {
    local deadline number
    # Job control gives each owned background job a dedicated process group on Linux and macOS.
    Xvfb -displayfd 3 -screen 0 1280x1024x24 -nolisten tcp \
        3> "$smoke_work/display-number" > "$smoke_work/virtual-display.log" 2>&1 &
    smoke_display_pid=$!
    deadline=$((SECONDS + 30))
    while [[ ! -s $smoke_work/display-number ]]; do
        kill -0 "$smoke_display_pid" 2>/dev/null || { smoke_fail "Xvfb exited without publishing a display number"; return 1; }
        ((SECONDS < deadline)) || { smoke_fail "Xvfb did not publish its ready display number"; return 1; }
        sleep 0.25
    done
    IFS= read -r number < "$smoke_work/display-number"
    [[ $number != *[!0-9]* && -n $number ]] || { smoke_fail "Xvfb published an invalid display number"; return 1; }
    smoke_environment+=("DISPLAY=:$number")
}

# The application emits this marker only after desktop startup completes. A fixed
# sleep could accept a broken or still-starting process; check both log and liveness.
smoke_wait_ready() {
    local deadline=$((SECONDS + 180))
    while ! grep -qF VISUAL_AGENT_DESKTOP_READY "$smoke_work/application.log"; do
        kill -0 "$smoke_app_pid" 2>/dev/null || { smoke_fail "Application exited before readiness"; return 1; }
        ((SECONDS < deadline)) || { smoke_fail "Readiness marker was not emitted within 180 seconds"; return 1; }
        sleep 0.25
    done
}

# Readiness must also produce real server-owned state in the isolated data root.
# Allow a bounded database flush, then require workspace creation and a live app.
smoke_verify_data() {
    local deadline=$((SECONDS + 10))
    while [[ ! -s $smoke_work/server-data/visual-agent.db.mv.db ]]; do
        ((SECONDS < deadline)) || { smoke_fail "The application did not create its isolated H2 database"; return 1; }
        sleep 0.25
    done
    [[ -d $smoke_work/server-data/workspace ]] || { smoke_fail "The application did not create its isolated workspace"; return 1; }
    kill -0 "$smoke_app_pid" 2>/dev/null || smoke_fail "Application exited immediately after readiness"
}

# Validate inputs before creating scratch data or changing system package state.
# Resolve the artifact before changing directories so paths containing spaces and
# relative paths retain their meaning throughout installation and application launch.
smoke_main() {
    [[ $# == 3 ]] || { smoke_fail "Usage: $0 linux|macos deb|rpm|appimage|dmg|jar ARTIFACT"; return 1; }
    local platform=$1 kind=$2 artifact=$3
    case "$platform:$kind" in linux:deb|linux:rpm|linux:appimage|linux:jar|macos:dmg|macos:jar) ;; *) smoke_fail "Unsupported platform/artifact combination: $platform:$kind"; return 1 ;; esac
    [[ -f $artifact && -s $artifact ]] || { smoke_fail "Release artifact must be a non-empty file"; return 1; }
    artifact=$(cd "$(dirname "$artifact")" && pwd -P)/$(basename "$artifact")
    smoke_repository=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
    mkdir -p "$smoke_repository/build"
    smoke_work=$(mktemp -d "$smoke_repository/build/visual-agent-smoke.XXXXXXXX")
    smoke_app_pid= smoke_display_pid= smoke_installed= smoke_device=
    # Install cleanup immediately after mktemp, including explicit signal exits.
    trap smoke_cleanup EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
    set -m
    mkdir "$smoke_work/home"
    # Pass isolation settings only to the application, preserving the harness's own
    # home and tool environment. JAVA_TOOL_OPTIONS also reaches bundled JVM launchers.
    smoke_environment=(
        "HOME=$smoke_work/home" "XDG_DATA_HOME=$smoke_work/xdg-data" "XDG_CONFIG_HOME=$smoke_work/xdg-config"
        "JAVA_TOOL_OPTIONS=-Dvisualagent.startup.auto-start-local=true -Duser.home=\"$smoke_work/home\" -Dvisual-agent.server.data-root=\"$smoke_work/server-data\" -Dlogging.file.name=\"$smoke_work/spring.log\""
    )
    if [[ $platform == linux ]]; then
        # A native package must prove that its bundled runtime is sufficient. The
        # JAR path deliberately retains its separate system-JDK requirement.
        if [[ $kind != jar ]] && command -v java >/dev/null; then
            smoke_fail "Native package smoke must run without a system Java executable"
            return 1
        fi
        if [[ $kind == appimage ]]; then smoke_environment+=(APPIMAGE_EXTRACT_AND_RUN=1); fi
        smoke_linux_command "$kind" "$artifact"
        smoke_display
    else
        smoke_macos_command "$kind" "$artifact"
        # On macOS, exclude toolchain installations while keeping standard system
        # utilities available to the native bundle. Do not borrow its private JDK.
        if [[ $kind == dmg ]]; then smoke_environment+=(JAVA_HOME= PATH=/usr/bin:/bin:/usr/sbin:/sbin); fi
    fi
    cd "$smoke_work"
    # Capture the exact launched job, rather than searching process names that
    # could belong to the developer or another concurrently running smoke test.
    env "${smoke_environment[@]}" "${smoke_command[@]}" > "$smoke_work/application.log" 2>&1 &
    smoke_app_pid=$!
    smoke_wait_ready
    smoke_verify_data
    smoke_stop "$smoke_app_pid" Application
    smoke_app_pid=
    printf 'Verified %s %s startup and isolated database creation.\n' "$platform" "$kind"
}

# Sourcing exposes functions to contract tests without installing or launching apps.
if [[ ${BASH_SOURCE[0]} == "$0" ]]; then smoke_main "$@"; fi
