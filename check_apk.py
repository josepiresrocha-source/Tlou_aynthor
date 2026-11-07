#!/usr/bin/env python3
"""Check the signed AYN Thor edition APK against the official v0.1 APK.

Every native, runtime and installer file must be byte-identical to v0.1, except the one
refreshed member of tools.zip and the added Turnip Thor driver. The launcher dex must be the
Thor edition's.
"""
import io
import sys
import zipfile

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from stage_apk import DRIVER_NAME, PATCH_MEMBER, payload  # noqa: E402


def main(original, built, patches, expect_driver):
    replacement = open(patches, "rb").read()
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(built) as new:
        new_names = set(new.namelist())
        checked = 0
        for name in old.namelist():
            if payload(name) is None or name.endswith("/"):
                continue
            if name not in new_names:
                raise SystemExit(f"missing from the Thor APK: {name}")
            if name == "assets/tools.zip":
                with zipfile.ZipFile(io.BytesIO(old.read(name))) as a, \
                        zipfile.ZipFile(io.BytesIO(new.read(name))) as b:
                    if a.namelist() != b.namelist():
                        raise SystemExit("tools.zip member list differs")
                    for member in a.namelist():
                        want = replacement if member == PATCH_MEMBER else a.read(member)
                        if b.read(member) != want:
                            raise SystemExit(f"tools.zip member differs: {member}")
            elif old.read(name) != new.read(name):
                raise SystemExit(f"payload differs: {name}")
            checked += 1
        extra = sorted(n for n in new_names - set(old.namelist())
                       if payload(n) is not None and not n.endswith("/"))
        allowed = {f"assets/drivers/{DRIVER_NAME}", f"assets/drivers/{DRIVER_NAME}.sha256"}
        if set(extra) - allowed:
            raise SystemExit(f"unexpected payload files: {sorted(set(extra) - allowed)}")
        if expect_driver and set(extra) != allowed:
            raise SystemExit("Turnip Thor driver is not bundled")
        dex = new.read("classes.dex")
        for cls in (b"Lro/cobrabm/fexdroid/ThorProfile;", b"Lro/cobrabm/fexdroid/ThorEnv;"):
            if cls not in dex:
                raise SystemExit(f"launcher dex lacks {cls.decode()}")
    print(f"OK: {checked} payload files identical to v0.1 (tools.zip: only {PATCH_MEMBER}); "
          f"added: {', '.join(extra) or 'nothing'}")


if __name__ == "__main__":
    if len(sys.argv) not in (4, 5):
        raise SystemExit("usage: check_apk.py original.apk thor.apk tlou_patches.py [--driver]")
    main(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:] == ["--driver"])
