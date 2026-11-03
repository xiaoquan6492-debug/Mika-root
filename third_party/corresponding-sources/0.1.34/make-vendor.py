#!/usr/bin/env python3
"""Unpack verified .crate archives into a Cargo directory source. No execution."""
import argparse, hashlib, json, pathlib, re, tarfile

def safe_member(name, root):
    p = pathlib.PurePosixPath(name)
    if p.is_absolute() or ".." in p.parts:
        raise SystemExit(f"unsafe crate member: {name}")
    return p

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--crates", default=".", help="directory containing .crate archives")
    ap.add_argument("--out", required=True, help="vendor output directory")
    a = ap.parse_args()
    crates = pathlib.Path(a.crates)
    out = pathlib.Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    found = 0
    for crate in sorted(crates.glob("*.crate")):
        expected = hashlib.sha256(crate.read_bytes()).hexdigest()
        dest = out / crate.stem
        if dest.exists():
            raise SystemExit(f"refusing to replace existing {dest}")
        dest.mkdir()
        files = {}
        with tarfile.open(crate, "r:gz") as t:
            for m in t:
                if not m.isfile():
                    continue
                p = safe_member(m.name, crate.stem)
                parts = list(p.parts)
                if parts and parts[0] == crate.stem:
                    parts = parts[1:]
                if not parts:
                    continue
                target = dest.joinpath(*parts)
                data = t.extractfile(m).read()
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
                files["/".join(parts)] = hashlib.sha256(data).hexdigest()
        (dest / ".cargo-checksum.json").write_text(json.dumps({"files": files, "package": expected}, sort_keys=True) + "\n", encoding="utf-8")
        found += 1
    if not found:
        raise SystemExit("no .crate archives found")
    print(f"prepared {found} vendor packages in {out}")

if __name__ == "__main__":
    main()
