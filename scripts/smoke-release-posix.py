#!/usr/bin/env python3
"""Install or launch one packaged desktop artifact and verify its real startup path."""

from __future__ import annotations

import argparse
import os
import pathlib
import select
import shutil
import signal
import subprocess
import sys
import tempfile
import time

READY_MARKER = "VISUAL_AGENT_DESKTOP_READY"
STARTUP_TIMEOUT_SECONDS = 180
SHUTDOWN_TIMEOUT_SECONDS = 30


def run_checked(command: list[str], **kwargs: object) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, check=True, text=True, **kwargs)


def install_linux_artifact(kind: str, artifact: pathlib.Path) -> list[str]:
    if kind == "jar":
        return ["java", "-jar", str(artifact)]
    if kind == "deb":
        run_checked(["apt-get", "install", "--yes", str(artifact)])
        inventory = run_checked(["dpkg-query", "--listfiles", "visual-agent"], capture_output=True)
    elif kind == "rpm":
        run_checked(["dnf", "install", "--assumeyes", str(artifact)])
        inventory = run_checked(["rpm", "--query", "--list", "visual-agent"], capture_output=True)
    elif kind == "appimage":
        return [str(artifact)]
    else:
        raise ValueError(f"Unsupported Linux artifact kind: {kind}")
    launchers = [path for path in inventory.stdout.splitlines() if path.endswith("/bin/Visual Agent")]
    if len(launchers) != 1:
        raise RuntimeError(f"Expected one installed launcher in package metadata, found {len(launchers)}")
    return launchers


def macos_command(
    kind: str,
    artifact: pathlib.Path,
    work: pathlib.Path,
) -> tuple[list[str], str | None]:
    if kind == "jar":
        return ["java", "-jar", str(artifact)], None
    if kind != "dmg":
        raise ValueError(f"Unsupported macOS artifact kind: {kind}")
    mount = work / "mounted"
    mount.mkdir()
    attached = run_checked(
        ["hdiutil", "attach", "-nobrowse", "-readonly", "-mountpoint", str(mount), str(artifact)],
        capture_output=True,
    )
    device = next((line.split()[0] for line in attached.stdout.splitlines() if line.startswith("/dev/disk")), None)
    if device is None:
        raise RuntimeError("Could not identify the mounted disk image device")
    try:
        app = next(mount.glob("*.app"), None)
        if app is None:
            raise RuntimeError("The DMG did not contain a .app bundle")
        binaries = [path for path in (app / "Contents" / "MacOS").iterdir() if path.is_file() and os.access(path, os.X_OK)]
        if len(binaries) != 1:
            raise RuntimeError(f"Expected one app executable, found {len(binaries)}")
        return [str(binaries[0])], device
    except Exception:
        run_checked(["hdiutil", "detach", device, "-force"])
        raise


def wait_for_ready(process: subprocess.Popen[bytes], log_path: pathlib.Path) -> None:
    deadline = time.monotonic() + STARTUP_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if log_path.exists() and READY_MARKER in log_path.read_text(errors="replace"):
            return
        if process.poll() is not None:
            raise RuntimeError(f"Application exited before readiness (exit code {process.returncode})")
        time.sleep(0.25)
    raise TimeoutError(f"Readiness marker was not emitted within {STARTUP_TIMEOUT_SECONDS} seconds")


def verify_database(data_root: pathlib.Path) -> None:
    database = data_root / "visual-agent.db.mv.db"
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        if database.is_file() and database.stat().st_size > 0:
            return
        time.sleep(0.25)
    raise RuntimeError(f"The application did not create its isolated H2 database: {database}")


def verify_workspace(data_root: pathlib.Path) -> None:
    workspace = data_root / "workspace"
    if not workspace.is_dir():
        raise RuntimeError(f"The application did not create its isolated workspace: {workspace}")


def stop_process(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        if process.returncode not in (0, -signal.SIGTERM, 128 + signal.SIGTERM):
            raise RuntimeError(f"Application exited unexpectedly with code {process.returncode}")
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=SHUTDOWN_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=5)
        raise TimeoutError("The application did not stop after SIGTERM")
    if process.returncode not in (0, -signal.SIGTERM, 128 + signal.SIGTERM):
        raise RuntimeError(f"Application shutdown returned {process.returncode}")


def uninstall_linux_artifact(kind: str) -> None:
    if kind == "deb":
        run_checked(["apt-get", "remove", "--yes", "visual-agent"])
    elif kind == "rpm":
        run_checked(["dnf", "remove", "--assumeyes", "visual-agent"])


def start_virtual_display(env: dict[str, str], work: pathlib.Path) -> subprocess.Popen[bytes]:
    read_fd, write_fd = os.pipe()
    server = None
    try:
        with (work / "virtual-display.log").open("wb") as output:
            server = subprocess.Popen(
                ["Xvfb", "-displayfd", str(write_fd), "-screen", "0", "1280x1024x24", "-nolisten", "tcp"],
                pass_fds=(write_fd,),
                stdout=output,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
        os.close(write_fd)
        write_fd = None
        if not select.select([read_fd], [], [], 30)[0]:
            raise TimeoutError("Xvfb did not publish its ready display number")
        number = os.read(read_fd, 32).decode("ascii").strip()
        if not number.isdigit():
            raise RuntimeError("Xvfb exited without publishing a display number")
        env["DISPLAY"] = f":{number}"
        return server
    except Exception:
        if server is not None:
            stop_process(server)
        raise
    finally:
        os.close(read_fd)
        if write_fd is not None:
            os.close(write_fd)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("platform", choices=("linux", "macos"))
    parser.add_argument("kind", choices=("deb", "rpm", "appimage", "dmg", "jar"))
    parser.add_argument("artifact", type=pathlib.Path)
    args = parser.parse_args()
    artifact = args.artifact.resolve(strict=True)
    if not artifact.is_file() or artifact.stat().st_size == 0:
        parser.error("Release artifact must be a non-empty file")
    if args.platform == "linux" and args.kind in ("dmg",):
        parser.error("DMG is not a Linux artifact")
    if args.platform == "macos" and args.kind not in ("dmg", "jar"):
        parser.error("macOS smoke accepts only DMG and JAR artifacts")

    process: subprocess.Popen[bytes] | None = None
    display_server: subprocess.Popen[bytes] | None = None
    mount_device: str | None = None
    installed = args.platform == "linux" and args.kind in ("deb", "rpm")
    with tempfile.TemporaryDirectory(prefix="visual-agent-smoke-") as temporary:
        work = pathlib.Path(temporary)
        data_root = work / "server-data"
        home = work / "home"
        home.mkdir()
        log_path = work / "application.log"
        env = os.environ.copy()
        env.update(
            {
                "HOME": str(home),
                "XDG_DATA_HOME": str(work / "xdg-data"),
                "XDG_CONFIG_HOME": str(work / "xdg-config"),
                "JAVA_TOOL_OPTIONS": (
                    f"-Dvisualagent.startup.auto-start-local=true "
                    f'-Duser.home="{home}" '
                    f'-Dvisual-agent.server.data-root="{data_root}" '
                    f'-Dlogging.file.name="{work / "spring.log"}"'
                ),
            },
        )
        if args.platform == "linux" and args.kind == "appimage":
            env["APPIMAGE_EXTRACT_AND_RUN"] = "1"
        try:
            if args.platform == "linux":
                if args.kind != "jar" and shutil.which("java"):
                    raise RuntimeError("Native package smoke must run without a system Java executable")
                if args.kind == "appimage":
                    artifact.chmod(artifact.stat().st_mode | 0o111)
                command = install_linux_artifact(args.kind, artifact)
            else:
                command, mount_device = macos_command(args.kind, artifact, work)
                if args.kind == "dmg":
                    env["JAVA_HOME"] = ""
                    env["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin"
            if args.platform == "linux":
                display_server = start_virtual_display(env, work)
            with log_path.open("wb") as output:
                process = subprocess.Popen(
                    command,
                    cwd=work,
                    env=env,
                    stdout=output,
                    stderr=subprocess.STDOUT,
                    start_new_session=True,
                )
            wait_for_ready(process, log_path)
            verify_database(data_root)
            verify_workspace(data_root)
            if process.poll() is not None:
                raise RuntimeError(f"Application exited immediately after readiness with code {process.returncode}")
            print(f"Verified {args.platform} {args.kind} startup and isolated database creation.")
            stop_process(process)
            process = None
        except Exception:
            for log in (log_path, work / "spring.log", work / "virtual-display.log"):
                if log.exists():
                    print(log.read_text(errors="replace")[-32768:], file=sys.stderr)
            raise
        finally:
            try:
                if process is not None:
                    stop_process(process)
            finally:
                try:
                    if display_server is not None:
                        stop_process(display_server)
                finally:
                    try:
                        if installed:
                            uninstall_linux_artifact(args.kind)
                    finally:
                        if mount_device is not None:
                            run_checked(["hdiutil", "detach", mount_device, "-force"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
