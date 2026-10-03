#!/usr/bin/env python3
"""
KeyStudio dev harness.

One script so the toolchain (correct JDK, SDK paths, gradle, adb, emulator)
is configured in exactly one place -- and only needs approving once.

Usage:
    python tools/kb.py build [gradle args...]   # default task: :app:assembleDebug
    python tools/kb.py wrapper                  # (re)generate the gradle wrapper
    python tools/kb.py clean
    python tools/kb.py install                  # assemble + install debug on device
    python tools/kb.py devices
    python tools/kb.py emu [avd-name]           # boot an emulator (background)
    python tools/kb.py wait                     # block until a device is ready
    python tools/kb.py info                     # device model / android version / density
    python tools/kb.py ime-list                 # installed input methods
    python tools/kb.py ime-on                   # enable + select the KeyStudio IME
    python tools/kb.py ime-off                  # restore the previous IME
    python tools/kb.py logcat [tag-filter]      # tail logcat (Ctrl-C to stop)
    python tools/kb.py shot [out.png]           # screenshot the device
    python tools/kb.py doctor                   # verify the whole toolchain
    python tools/kb.py avd-create [name] [pkg]  # create a clean AVD for this project
    python tools/kb.py avd-list                 # list AVDs
    python tools/kb.py pair <ip:port> <code>    # one-time wifi debugging pairing
    python tools/kb.py connect [ip:port]        # connect to the phone over wifi
    python tools/kb.py use [serial]             # pin commands to one device
"""
from __future__ import annotations

import os
import subprocess
import sys
import time
from pathlib import Path

# --------------------------------------------------------------------------
# Toolchain locations. Machine-specific paths live in local.properties
# (git-ignored), next to the sdk.dir Android Studio already writes there:
#
#     sdk.dir=D:/path/to/Android_SDK
#     kb.java.home=C:/path/to/jdk-21
#     kb.wifi.host=192.168.1.20:37000      # optional, for `kb.py connect`
#
# Falling back to ANDROID_HOME / KB_JAVA_HOME / JAVA_HOME from the environment.
# --------------------------------------------------------------------------
PROJECT = Path(__file__).resolve().parent.parent


def _local_properties() -> dict[str, str]:
    f = PROJECT / "local.properties"
    props = {}
    if f.exists():
        for line in f.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip().replace("\\:", ":").replace("\\\\", "\\")
    return props


_PROPS = _local_properties()
JAVA_HOME = Path(_PROPS.get("kb.java.home")
                 or os.environ.get("KB_JAVA_HOME")
                 or os.environ.get("JAVA_HOME", ""))
ANDROID_HOME = Path(_PROPS.get("sdk.dir") or os.environ.get("ANDROID_HOME", ""))
GRADLE_USER_HOME = Path(os.environ.get("GRADLE_USER_HOME")
                        or Path.home() / ".gradle")
GRADLE_DIST = next(
    (GRADLE_USER_HOME / "wrapper" / "dists" / "gradle-9.5.0-bin").glob("*/gradle-9.5.0"),
    GRADLE_USER_HOME / "wrapper" / "dists" / "gradle-9.5.0-bin" / "gradle-9.5.0",
)

ADB = ANDROID_HOME / "platform-tools" / "adb.exe"
EMULATOR = ANDROID_HOME / "emulator" / "emulator.exe"

APP_ID = "io.keystudio.kb"
IME_CLASS = "io.keystudio.kb.ime.KeyStudioInputMethodService"
# The framework's canonical id abbreviates the class relative to the package
# ("pkg/.ime.Foo"); `ime enable` rejects the fully-qualified spelling, so the
# real id is read back from the device rather than assumed.
IME_ID_FALLBACK = f"{APP_ID}/.ime.KeyStudioInputMethodService"

# Medium_Phone has a corrupted package database and resolves no third-party
# component; KeyStudio_API31 is a clean AVD matching the Poco X3's Android 12.
DEFAULT_AVD = "KeyStudio_API31"

# The phone over wireless debugging. The port changes whenever wireless
# debugging is toggled, so this is only a convenience default.
DEFAULT_WIFI_HOST = _PROPS.get("kb.wifi.host")


DEVICE_FILE = PROJECT / ".kb-device"


def selected_device() -> str | None:
    """Serial chosen by `kb.py use`, so adb and Gradle agree on the target."""
    if DEVICE_FILE.exists():
        s = DEVICE_FILE.read_text(encoding="utf-8").strip()
        return s or None
    return None


def env() -> dict:
    e = dict(os.environ)
    e["JAVA_HOME"] = str(JAVA_HOME)
    e["ANDROID_HOME"] = str(ANDROID_HOME)
    e["ANDROID_SDK_ROOT"] = str(ANDROID_HOME)
    e["PATH"] = f"{JAVA_HOME / 'bin'}{os.pathsep}{e.get('PATH', '')}"
    # Gradle's installDebug and every adb call honour ANDROID_SERIAL, so one
    # setting keeps them pointed at the same device.
    serial = selected_device()
    if serial:
        e["ANDROID_SERIAL"] = serial
    return e


def run(cmd, *, cwd=PROJECT, check=True, capture=False, quiet=False):
    cmd = [str(c) for c in cmd]
    if not quiet:
        print(f"$ {' '.join(cmd)}", flush=True)
    if capture:
        p = subprocess.run(cmd, cwd=cwd, env=env(), check=False,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           text=True, errors="replace")
        if check and p.returncode != 0:
            print(p.stdout)
            sys.exit(p.returncode)
        return p.stdout
    p = subprocess.run(cmd, cwd=cwd, env=env(), check=False)
    if check and p.returncode != 0:
        sys.exit(p.returncode)
    return ""


# --------------------------------------------------------------------------
# Gradle
# --------------------------------------------------------------------------
def gradle_cmd() -> list:
    """Prefer the project wrapper; fall back to the cached distribution."""
    wrapper = PROJECT / "gradlew.bat"
    if wrapper.exists():
        return [wrapper]
    launcher = GRADLE_DIST / "lib" / "gradle-launcher-9.5.0.jar"
    if not launcher.exists():
        sys.exit(f"No gradle wrapper and no cached distribution at {launcher}")
    return [JAVA_HOME / "bin" / "java.exe", "-classpath", launcher,
            "org.gradle.launcher.GradleMain"]


def gradle(args, check=True, capture=False):
    return run(gradle_cmd() + list(args), check=check, capture=capture)


def cmd_wrapper(argv):
    gradle(["wrapper", "--gradle-version", "9.5.0",
            "--distribution-type", "bin", "--no-daemon"])


def cmd_build(argv):
    gradle(list(argv) or [":app:assembleDebug"])


def cmd_clean(argv):
    gradle(["clean"] + list(argv))


def cmd_install(argv):
    gradle([":app:installDebug"] + list(argv))


# --------------------------------------------------------------------------
# Device / adb
# --------------------------------------------------------------------------
def adb(args, check=True, capture=True, quiet=True):
    return run([ADB] + list(args), check=check, capture=capture, quiet=quiet)


def device_online() -> bool:
    out = adb(["devices"], check=False)
    return any(line.strip().endswith("\tdevice") for line in out.splitlines()[1:])


def cmd_devices(argv):
    print(adb(["devices", "-l"], check=False, quiet=False))
    print(f"selected: {selected_device() or '(none - first device wins)'}")


def cmd_use(argv):
    """Pin every later command to one device, or clear the pin with no args."""
    if not argv:
        DEVICE_FILE.unlink(missing_ok=True)
        print("cleared device selection")
        return
    DEVICE_FILE.write_text(argv[0], encoding="utf-8")
    print(f"targeting {argv[0]}")


def cmd_wait(argv):
    print("waiting for a device...", flush=True)
    adb(["wait-for-device"], quiet=False)
    for _ in range(120):
        out = adb(["shell", "getprop", "sys.boot_completed"], check=False).strip()
        if out == "1":
            print("device ready")
            return
        time.sleep(2)
    sys.exit("timed out waiting for boot")


def cmd_emu(argv):
    avd = argv[0] if argv else DEFAULT_AVD
    print(f"booting emulator {avd} (background)...")
    subprocess.Popen(
        [str(EMULATOR), "-avd", avd, "-gpu", "host", "-no-boot-anim"],
        env=env(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        creationflags=getattr(subprocess, "DETACHED_PROCESS", 0),
    )
    cmd_wait([])


PROPS = [
    ("model", "ro.product.model"),
    ("device", "ro.product.device"),
    ("manufacturer", "ro.product.manufacturer"),
    ("android", "ro.build.version.release"),
    ("sdk", "ro.build.version.sdk"),
    ("abi", "ro.product.cpu.abi"),
    ("density", "ro.sf.lcd_density"),
]


def cmd_info(argv):
    if not device_online():
        sys.exit("no device. plug the phone in (USB debugging on) or run: kb.py emu")
    for label, prop in PROPS:
        print(f"  {label:14s} {adb(['shell', 'getprop', prop], check=False).strip()}")
    print(f"  {'screen':14s} {adb(['shell', 'wm', 'size'], check=False).strip()}")
    print(f"  {'wm density':14s} {adb(['shell', 'wm', 'density'], check=False).strip()}")
    refresh = adb(["shell", "dumpsys", "display", "|", "grep", "-i", "fps"], check=False)
    if refresh.strip():
        print(f"  refresh        {refresh.strip().splitlines()[0][:100]}")


# --------------------------------------------------------------------------
# IME control
# --------------------------------------------------------------------------
def cmd_ime_list(argv):
    print(adb(["shell", "ime", "list", "-s"], check=False))


def resolve_ime_id() -> str:
    """The id exactly as the framework spells it, or the fallback if not listed yet."""
    listing = adb(["shell", "ime", "list", "-a", "-s"], check=False)
    for line in listing.splitlines():
        line = line.strip()
        if line.startswith(APP_ID + "/"):
            return line
    return IME_ID_FALLBACK


def cmd_ime_on(argv):
    prev = adb(["shell", "settings", "get", "secure", "default_input_method"],
               check=False).strip()
    if prev and prev != "null" and not prev.startswith(APP_ID):
        (PROJECT / ".kb-prev-ime").write_text(prev, encoding="utf-8")
        print(f"saved previous IME: {prev}")
    ime_id = resolve_ime_id()
    adb(["shell", "ime", "enable", ime_id], check=False, quiet=False)
    adb(["shell", "ime", "set", ime_id], check=False, quiet=False)
    print(f"active IME: {adb(['shell', 'settings', 'get', 'secure', 'default_input_method'], check=False).strip()}")


def cmd_ime_off(argv):
    f = PROJECT / ".kb-prev-ime"
    if not f.exists():
        sys.exit("no saved IME to restore")
    prev = f.read_text(encoding="utf-8").strip()
    adb(["shell", "ime", "set", prev], check=False, quiet=False)
    print(f"restored: {prev}")


# --------------------------------------------------------------------------
# Diagnostics
# --------------------------------------------------------------------------
def cmd_logcat(argv):
    args = [ADB, "logcat", "-v", "brief"]
    if argv:
        args += [f"{argv[0]}:V", "*:S"]
    else:
        args += ["KeyStudio:V", "AndroidRuntime:E", "*:S"]
    subprocess.run([str(a) for a in args], env=env())


def cmd_shot(argv):
    out = Path(argv[0]) if argv else PROJECT / "screenshot.png"
    raw = subprocess.run([str(ADB), "exec-out", "screencap", "-p"],
                         env=env(), stdout=subprocess.PIPE, check=False).stdout
    out.write_bytes(raw)
    print(f"saved {out} ({len(raw) // 1024} KB)")


def cmd_doctor(argv):
    ok = True

    def check(label, cond, detail=""):
        nonlocal ok
        ok &= bool(cond)
        print(f"  [{'OK ' if cond else 'FAIL'}] {label}{(' - ' + detail) if detail else ''}")

    print("toolchain:")
    check("JDK 21", (JAVA_HOME / "bin" / "java.exe").exists(),
          str(JAVA_HOME) if str(JAVA_HOME) != "." else "set kb.java.home in local.properties")
    check("Android SDK", ANDROID_HOME.exists(),
          str(ANDROID_HOME) if str(ANDROID_HOME) != "." else "set sdk.dir in local.properties")
    check("adb", ADB.exists())
    check("emulator", EMULATOR.exists())
    check("gradle dist", GRADLE_DIST.exists())
    check("wrapper", (PROJECT / "gradlew.bat").exists(), "run: kb.py wrapper")
    check("local.properties", (PROJECT / "local.properties").exists())
    print("java:")
    v = run([JAVA_HOME / "bin" / "java.exe", "-version"], capture=True,
            check=False, quiet=True)
    print("   " + v.strip().splitlines()[0] if v.strip() else "   (none)")
    print("device:")
    check("device connected", device_online(), "optional")
    sys.exit(0 if ok else 1)



def cmd_avd_create(argv):
    """Create a clean AVD for this project, so other projects' emulators stay untouched."""
    name = argv[0] if argv else "KeyStudio_API31"
    pkg = argv[1] if len(argv) > 1 else "system-images;android-31;google_apis_playstore;x86_64"
    avdmanager = ANDROID_HOME / "cmdline-tools" / "latest" / "bin" / "avdmanager.bat"
    p = subprocess.run(
        [str(avdmanager), "create", "avd", "-n", name, "-k", pkg,
         "-d", "pixel_5", "--force"],
        env=env(), input="no" + chr(10), text=True, check=False,
    )
    if p.returncode != 0:
        sys.exit(p.returncode)
    print(f"created AVD {name}")


def cmd_avd_list(argv):
    run([EMULATOR, "-list-avds"])



PAIR_HELP = [
    "usage: kb.py pair <ip:pairingPort> <6-digit-code>",
    "  On the Poco X3: Settings > Additional settings > Developer options",
    "                 > Wireless debugging > Pair device with pairing code",
    "  That dialog shows its own port, which is NOT the connect port.",
]

CONNECT_HELP = [
    "",
    "Port open but connection refused means this host is not paired yet.",
    "On the phone: Settings > Developer options > Wireless debugging",
    "              > Pair device with pairing code",
    "then run:  python tools/kb.py pair <ip:pairingPort> <code>",
    "",
    "The pairing port differs from the connect port, and the connect port",
    "changes every time wireless debugging is toggled off and on.",
]


def cmd_pair(argv):
    """One-time handshake required by Android 11+ wireless debugging."""
    if len(argv) < 2:
        sys.exit(chr(10).join(PAIR_HELP))
    host, code = argv[0], argv[1]
    p = subprocess.run([str(ADB), "pair", host, code], env=env(), check=False)
    if p.returncode:
        sys.exit(p.returncode)
    print("paired")


def cmd_connect(argv):
    """Connect to the phone over wifi. Pairing must have happened once before."""
    host = argv[0] if argv else DEFAULT_WIFI_HOST
    if not host:
        sys.exit("usage: kb.py connect <ip:port>  (or set kb.wifi.host in local.properties)")
    out = run([ADB, "connect", host], capture=True, check=False, quiet=False)
    print(out.strip())
    if "failed" in out or "cannot" in out:
        print(chr(10).join(CONNECT_HELP))
    print(adb(["devices", "-l"], check=False))


COMMANDS = {
    "build": cmd_build, "wrapper": cmd_wrapper, "clean": cmd_clean,
    "install": cmd_install, "devices": cmd_devices, "emu": cmd_emu,
    "wait": cmd_wait, "info": cmd_info, "ime-list": cmd_ime_list,
    "ime-on": cmd_ime_on, "ime-off": cmd_ime_off, "logcat": cmd_logcat,
    "shot": cmd_shot, "doctor": cmd_doctor,
    "avd-create": cmd_avd_create, "avd-list": cmd_avd_list,
    "pair": cmd_pair, "connect": cmd_connect, "use": cmd_use,
}


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help", "help"):
        print(__doc__)
        return
    cmd = sys.argv[1]
    if cmd not in COMMANDS:
        sys.exit(f"unknown command '{cmd}'. see: python tools/kb.py --help")
    COMMANDS[cmd](sys.argv[2:])


if __name__ == "__main__":
    main()
