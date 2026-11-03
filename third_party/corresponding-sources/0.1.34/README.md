# Mika-root 0.1.34 corresponding sources

These are **source archives, not APKs**. They are the corresponding-source set for the native/runtime assets
checked into this repository. The source tree itself contains no APK. The debug preview release
[v0.1.34-root.1](https://github.com/xiaoquan6492-debug/Mika-root/releases/tag/v0.1.34-root.1) should carry the
APK and the full archive reconstructed from these parts.

The full archive is split only to stay below Git file-size limits. Concatenate all parts in filename order to
reconstruct exactly:

`mika-0.1.34-gpl-sources.tar.gz`

- Full archive size: **111,367,173 bytes**
- Full archive SHA-256: `98d5c00805e13a89599ba94feaa50849602421e79294534a916e29fc0c43b538`
- Parts: see `SHA256SUMS`.
- The archive contains PRoot/talloc source archives and four patches; native build/relink scripts and source
  locks; 14 Alpine copyleft package recipes/upstream sources plus `SOURCE-EXTRA.tsv` install/trigger scripts
  from the pinned aports commits; the complete 27-crate libimagequant Cargo.lock source closure and licenses;
  preserved license/notice texts; and `SOURCE-MANIFEST.tsv`.

## Verify and concatenate

Linux/macOS:

```sh
cat mika-0.1.34-gpl-sources.tar.gz.part* > mika-0.1.34-gpl-sources.tar.gz
sha256sum -c SHA256SUMS
```

PowerShell 7 or Windows PowerShell 5.1 (binary-safe; do not use text redirection):

```powershell
$dir = (Get-Location).Path
$out = Join-Path $dir 'mika-0.1.34-gpl-sources.tar.gz'
$parts = Get-ChildItem -Path $dir -Filter 'mika-0.1.34-gpl-sources.tar.gz.part*' | Sort-Object Name
$fs = [System.IO.File]::Create($out)
try {
  foreach ($p in $parts) {
    $in = [System.IO.File]::OpenRead($p.FullName)
    try { $in.CopyTo($fs) } finally { $in.Dispose() }
  }
} finally { $fs.Dispose() }
Get-FileHash -Algorithm SHA256 $out
```

Verify the full SHA-256 above before extracting. The archive is a source archive; extracting it does not install
or run any binary. Offline Cargo/vendor instructions are in the archive at
`runtime/libimagequant-4.2.2-r0/cargo/README.md`; a copy of its safe extraction helper is in this directory as
`make-vendor.py`. Install/trigger scripts are beside their APKBUILDs.

## Provenance and limits

The package is assembled from the locked recipes under `tools/sandbox/`; native source hashes and Cargo
checksums were verified. Downloads were obtained from official locked upstream endpoints. No downloaded build
recipe, Cargo build script, proc macro, or crate code was executed. See the repository's
[`docs/root-fork.md`](../../../docs/root-fork.md), [`LICENSE`](../../../LICENSE),
[`NOTICE`](../../../NOTICE), and [`licenses/THIRD_PARTY_NOTICES.md`](../../../licenses/THIRD_PARTY_NOTICES.md).

These parts and hashes are included in this source checkout. After obtaining this checkout, reconstruct and
verify the full archive as documented above. The named debug preview release should attach that reconstructed
archive; this directory itself is not an APK or legal-compliance guarantee. Fork-specific ROOT bugs belong in
this fork's Issues, not in an upstream Mika support obligation; official upstream releases and community remain
upstream.
