#!/usr/bin/env python3
"""Stage the native payload of the official TLOU Android v0.1 APK for the AYN Thor edition.

The edition only rebuilds the Java launcher. Everything native (FEX, the shadPS4-based Vulkan
renderer, the Bionic runtime, Python and the PKG tools) is copied byte for byte from the
published APK, whose SHA-256 is pinned below. Two things are added on top:

  - android/tlou_patches.py from the author's commit bab5b59 replaces the copy inside
    tools.zip: it fixes the aim normalization at 720p/540p/360p (e89f357) and adds the
    optional job-sleep switch (7a242f0, default unchanged). The same in-place refresh the
    author's own launcher-only build uses (android/bionic/reuse_apk.py).
  - The Turnip Thor driver zip, as assets/drivers/turnip-thor-a740.zip plus its SHA-256.

Usage: stage_apk.py --apk v0.1.apk --out <tlou>/out/android-bionic
                    --patches tlou/android/tlou_patches.py --driver Turnip_Thor_v4_bN.zip
"""
import argparse
import copy
import hashlib
import io
import json
import sys
import zipfile
from pathlib import Path, PurePosixPath

APK_SHA256 = "ccbdbfeeb1ef807b853a85102f7d148d9fcb7c6c13c779823e10621ec9197f85"
PATCH_MEMBER = "source/android/tlou_patches.py"
DRIVER_NAME = "turnip-thor-a740.zip"
THOR_API = 33  # Android 13 on the AYN Thor


def payload(name):
    """Where an APK member goes in the Gradle build (same rule as reuse_apk.py)."""
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"unsafe APK path: {name}")
    if name.startswith("lib/arm64-v8a/"):
        return Path("apk-jni") / Path(name).relative_to("lib")
    if name == "assets/tools.zip" or name.startswith(("assets/runtime/", "assets/drivers/")):
        return Path("apk-assets") / Path(name).relative_to("assets")
    return None


def refresh_patch_table(tools_zip, replacement):
    original = tools_zip.read_bytes()
    with zipfile.ZipFile(io.BytesIO(original)) as old:
        if PATCH_MEMBER not in old.namelist():
            raise SystemExit(f"{PATCH_MEMBER} missing from tools.zip")
        out = io.BytesIO()
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as new:
            for info in old.infolist():
                data = replacement if info.filename == PATCH_MEMBER else old.read(info.filename)
                new.writestr(copy.copy(info), data)
        with zipfile.ZipFile(io.BytesIO(out.getvalue())) as new:
            if new.namelist() != old.namelist():
                raise SystemExit("tools.zip member list changed")
            for name in old.namelist():
                expected = replacement if name == PATCH_MEMBER else old.read(name)
                if new.read(name) != expected:
                    raise SystemExit(f"tools.zip member changed unexpectedly: {name}")
    tools_zip.write_bytes(out.getvalue())


def check_driver(path):
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        if "meta.json" not in names:
            raise SystemExit("driver zip has no meta.json")
        meta = json.loads(z.read("meta.json"))
        lib = meta.get("libraryName", "libvulkan_freedreno.so")
        if lib not in names:
            raise SystemExit(f"driver zip has no {lib}")
        if int(meta.get("minApi", 0)) > THOR_API:
            raise SystemExit(f"driver needs Android API {meta['minApi']}, the Thor has {THOR_API}")
        head = z.read(lib)[:20]
        if head[:4] != b"\x7fELF" or head[4] != 2 or int.from_bytes(head[18:20], "little") != 183:
            raise SystemExit("driver library is not an ARM64 ELF")
        return meta.get("name", "?")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--patches", type=Path, required=True)
    ap.add_argument("--driver", type=Path)
    args = ap.parse_args()

    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    if digest != APK_SHA256:
        raise SystemExit(f"APK SHA-256 {digest} is not the published v0.1 ({APK_SHA256})")

    count = 0
    with zipfile.ZipFile(args.apk) as z:
        for item in z.infolist():
            target = payload(item.filename)
            if target is None or item.is_dir():
                continue
            dest = args.out / target
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(z.read(item))
            count += 1
    print(f"staged {count} native/runtime/driver files from the v0.1 APK")

    refresh_patch_table(args.out / "apk-assets" / "tools.zip", args.patches.read_bytes())
    print(f"tools.zip: {PATCH_MEMBER} refreshed (aim fix for 720p/540p/360p)")

    if args.driver:
        name = check_driver(args.driver)
        drivers = args.out / "apk-assets" / "drivers"
        data = args.driver.read_bytes()
        (drivers / DRIVER_NAME).write_bytes(data)
        sha = hashlib.sha256(data).hexdigest()
        (drivers / (DRIVER_NAME + ".sha256")).write_text(f"{sha}  {DRIVER_NAME}\n")
        print(f"bundled driver: {name} ({args.driver.name}, sha256 {sha})")
    else:
        print("no driver bundled: the launcher keeps the stock Turnip R1 as default")
    return 0


if __name__ == "__main__":
    sys.exit(main())
