# Mika-root: unofficial ROOT-enhanced fork

**Status: debug-signed ROOT preview. The first fork release is [v0.1.34-root.1](https://github.com/xiaoquan6492-debug/Mika-root/releases/tag/v0.1.34-root.1); it is not a stable or production release.**

The English README and all five translated READMEs carry an equivalent fork/debug-preview banner. This
document remains the fork-specific source of truth.

Mika-root is an **unofficial** fork of [Mika](https://github.com/wangzan101/Mika), maintained at
[xiaoquan6492-debug/Mika-root](https://github.com/xiaoquan6492-debug/Mika-root). It is not affiliated with,
endorsed by, or supported by the upstream Mika author. The upstream project, its release downloads, update
service, community channels and any donation links belong to upstream Mika, not to this fork.

This document explains what the ROOT features do, what they do **not** protect, how the fork was assembled,
and the limits of the debug preview binary.

## What is different

Mika-root starts from upstream Mika **0.1.34 (versionCode 35)** at upstream commit
`4ce3ca67e8cf8af8323a4d74505f51fd94178137`. The accepted local tree adds the ROOT work that originated in
upstream's `feat/root-mode` branch, tag `v0.1.33-root`
(`fb54ce9f1e76a28ede7beb6383847e610c97886a`), adapted with the safety fixes described below. It also carries
four reviewed fixes from upstream pull requests:

| PR | Head SHA used | Author | Change carried into this tree |
|---|---|---|---|
| [#233](https://github.com/wangzan101/Mika/pull/233) | `1a3c97c1783387d483ba3d0650a505708a13fcc6` | destire-mio | Reject chunked HTTP bodies before an integer-overflow can bypass the size limit |
| [#258](https://github.com/wangzan101/Mika/pull/258) | `09ddb1bf3facc6b0b870114cdb8b3625031e4492` | CatCatUncle | Validate complete child JSON before truncation; keep a bounded stored preview |
| [#255](https://github.com/wangzan101/Mika/pull/255) | `6d242ab859d1a6c993b575a319713bc2eccaf560` | kkkaqi | Read and close MCP HTTP bodies on the callback path; preserve cancellation and DELETE authentication |
| [#242](https://github.com/wangzan101/Mika/pull/242) | `c33c2485b158cfd0547d781f8c796bf8c59d3c32` | 15026087259ml-afk | Add DeepSeek's documented `none/low/high/max` reasoning options where the model listing does not declare them |

The ROOT feature is not a claim of sole original authorship. The root-mode branch and its commits are upstream
work; this fork retains the attribution and adds only the reviewed integration and documentation changes.

## The two ROOT switches

On a rooted phone, **Settings › Phone control › Root** shows two independent switches. Both default to **off**,
and the group is shown only when Mika can find `su`. Turning on one does not authorize the other.

1. **Use root to start the phone helper** starts the same built-in helper without Wireless debugging or a
   computer. The helper is launched through `su 2000 -c`, so it runs as **UID 2000 (shell)**, not as UID 0. The
   app waits until it reads `id -u == 2000` before handing over the one-time nonce. The existing handoff checks
   (UID/PID/Binder/APK/signature/nonce identity) remain in place; the app itself and the helper are never turned
   into a UID-0 process.
2. **Allow Mika to run root commands** enables the `root_run_command` tool. That command runs with actual root
   privileges through `su`. It is a separate switch from starting the helper and must be enabled explicitly.

Turning off the root-command switch stops new root commands. Unpairing phone control turns off both switches
and clears their grants. A denied or unresponsive `su` is not retried in a loop; the settings screen explains
the result and offers an explicit retry.

## `root_run_command`: approval and availability

The tool accepts a command (up to 4,000 characters) and a timeout (1–300 seconds; default 30). Standard output
and standard error are collected separately with a **1 MiB cap per stream**; excess bytes are counted as dropped,
and the returned tool result may also be clipped. NUL characters are rejected. Root commands are globally
serialized across tasks: a second task waits for the first, the wait is cancellable, and the switch,
authorization and task source are checked again after the lock is acquired.

**Ask mode:** every root command, including a read-only command, first displays the **complete command** and
requires approval for that one command. Approval is single-use. A changed setup, a different command, or an
expired confirmation cannot reuse it.

**Autonomous mode:** root commands can run without a prompt only when **all** of the following are true:

- the user explicitly enabled **Allow Mika to run root commands**;
- the task was started as an explicitly autonomous, attended **UI** task;
- the command is actually needed by that task.

Non-UI sources, unattended/scheduled work, child tasks, and condition-check runs do not get the root port. The
port itself rechecks the live switch, the `su` grant, the task source and attendance before every execution;
a task cannot retain a stale positive permission after the user revokes it.

The flow is not intended to make root safe for arbitrary use. Commands can modify or delete any data the root
user can reach, read other apps' private data, change system settings, or start persistent processes. Use the
switch only on a phone you control, keep the switch off unless required, use Ask mode for sensitive work, and
review the exact command before approving it.

## What this does not protect

- **The normal app/sandbox boundary is not preserved for root commands.** A root command runs outside the
  ordinary Android app sandbox and outside the PRoot Linux sandbox. It can reach data and system areas that the
  normal sandbox deliberately cannot.
- **The phone-tool `su` check is lexical, not a security sandbox.** Obvious `su` usage in the regular
  `phone_system_command` path is refused and redirected to `root_run_command`, but encoded, indirect, or
  external ways to escalate are not comprehensively blocked. Do not claim that the regular shell is safe
  because of this check.
- **Process cleanup is bounded, not absolute.** Cancellation or timeout closes streams and terminates the
  direct `su` process Mika started; a detached grandchild or a process that keeps a pipe open may survive, and
  a daemon reader thread may remain blocked on that pipe. The command contract cannot guarantee that every
  descendant or detached process is dead.
- **The root manager remains part of the trust boundary.** The user decides whether to grant su, and the root
  manager may have broader policies than this app can see or enforce.
- **No security audit is implied.** The tests below are functional and regression tests, not a penetration
  test or a proof that root commands are safe.

## Build, version and verification

The local accepted tree builds with **JDK 17** and Android SDK **compileSdk 36** using the project Gradle
wrapper. The version remains **0.1.34 / versionCode 35**; it is not a new release version.

Available local evidence is limited:

- The combined accepted local run covered **286 passing tests** (core 224, app 62; no failures/errors/skips in
  the explicit filters).
- A coordinator re-run covered **83 critical tests** with no failures/errors/skips. The two sets overlap; they
  must not be added to claim 369 independent tests.
- An earlier full-suite run recorded **135 failures/errors** without a same-environment HEAD baseline
  attribution. The full suites were not re-run and are **not green**. Do not represent the targeted results as
  full-suite coverage.
- The maintainer/user reports that real-device acceptance passed. **No public logs, screenshots, device
  identifiers, credentials, or independently witnessed test matrix are attached.** That statement is attributed
  to the maintainer/user, not to an independent verification performed by this repository.
- No real-device install matrix, WebView matrix, or live model/API validation is claimed. The debug preview APK is a development preview, not independently device-tested by this repository.

## Installation and signing warning

The application ID is still **`com.wangzan101.mika`**, the upstream package ID. The checked-in Gradle
configuration does not define a release signing identity; the debug preview is built and signed with the
developer's local debug keystore, while an official upstream release is signed with a different key. Its
debug certificate SHA-256 is
`1f74e23bf0d2f687afbce1c289b14c17120084ac280ef1ba31de92a179f95d6e`. This is an explicitly debuggable
development preview, **not** production signing and **not** a production-security assurance. Consequently:

- a debug build and the official app cannot be installed side by side under the same package ID;
- a build signed with another key cannot update the official app, and uninstalling to "fix" a signature mismatch
  can destroy app data;
- the inherited About › Check for updates path still points at the **upstream** `wangzan101/Mika` release API,
  not this fork. Use this fork's Releases manually for fork previews and do not assume that upstream offers a
  compatible fork build.

A production fork distribution would still need a deliberate, documented release-signing and update strategy.
This preview does not provide one.

## Licensing and source availability

The upstream repository's **Apache-2.0 `LICENSE`**, **`NOTICE`**, copyright attributions, and all third-party
license texts are retained verbatim; this fork does not relicense upstream or third-party work. Read
[`LICENSE`](../LICENSE), [`NOTICE`](../NOTICE) and
[`licenses/THIRD_PARTY_NOTICES.md`](../licenses/THIRD_PARTY_NOTICES.md).

The source tree still contains inherited native and rootfs assets, so it is **not all Apache-2.0**. The
notices identify, among other things:

- PRoot 5.4.0, GPL-2.0-or-later, including four tracked patches, built as `libproot.so` and
  `libproot_loader.so`;
- talloc 2.4.3, LGPL-3.0-or-later, statically linked into the PRoot executable;
- the Alpine 3.22.7 runtime and its Python/Node/Pillow/pypdf packages, under their individual licenses
  (GPL, LGPL, MIT, BSD, MPL, etc.).

The source pins and build recipes are retained under [`tools/sandbox/`](../tools/sandbox/README.md):
`sources.lock`, `runtime/sources.lock`, the patches, `build-native.sh`, `build-runtime.py`, and
`runtime-sources.py`. The notices state that a **binary APK release must publish the corresponding
`mika-<version>-gpl-sources.tar.gz`** alongside the APK.

**Corresponding-source status:** this checkout includes a complete, verified source archive,
`mika-0.1.34-gpl-sources.tar.gz` (111,367,173 bytes; SHA-256
`98d5c00805e13a89599ba94feaa50849602421e79294534a916e29fc0c43b538`). It contains PRoot/talloc original
archives, four patches and build/relink scripts; Alpine copyleft recipes, upstream sources, 11 pinned
install/trigger scripts and a `SOURCES.txt`/`SOURCE-EXTRA.tsv` index; the complete 27-crate Cargo.lock
dependency closure with licenses and `make-vendor.py`; and preserved license/notice texts. The verified archive
is split into three ordered parts under
[`third_party/corresponding-sources/0.1.34/`](../third_party/corresponding-sources/0.1.34/README.md).
**These parts and hashes are included in this source tree.** After obtaining this checkout, reconstruct and
verify the full archive using that README. The debug preview release page must carry the same reconstructed
archive. This source tree contains no APK. This document does **not** claim independently witnessed public
hosting or a legal guarantee. No source-offer contact is invented here.

## Upstream, community and donations

Use upstream Mika for its official releases, update service, issue tracker, community channels and any donation
mechanism. The fork's separate distribution is limited to the manual debug ROOT preview named above; it is not
a stable release program. This fork does not run a separate donation or community program. The upstream links
are kept in the README for attribution and convenience; they do **not** imply that this fork's code is the
upstream project or that donations go to this fork.

Fork-specific ROOT integration bugs and questions belong in [this fork's Issues](https://github.com/xiaoquan6492-debug/Mika-root/issues), not in an upstream Mika support obligation. Official upstream releases, the update service, community channels and donations remain upstream.

## Provenance summary

- Upstream base: `wangzan101/Mika` at `4ce3ca67e8cf8af8323a4d74505f51fd94178137`, version 0.1.34 / code 35.
- ROOT origin: upstream `feat/root-mode`, tag `v0.1.33-root`,
  commit `fb54ce9f1e76a28ede7beb6383847e610c97886a`; adapted with the reviewed safety fixes.
- Reviewed PRs and authors: the table at the top of this document; each link is to the upstream pull request.
- File-by-file adaptation/notice manifest: [docs/fork-modifications.md](fork-modifications.md).
- Fork: [xiaoquan6492-debug/Mika-root](https://github.com/xiaoquan6492-debug/Mika-root).
- This fork does not claim that the root-mode code or the four PR changes were written solely by its maintainer.
