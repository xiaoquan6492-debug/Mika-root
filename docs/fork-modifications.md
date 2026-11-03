# Mika-root fork-source modification manifest

Date: 2026-11-03. This file records file-by-file provenance for the accepted ROOT + reviewed-PR changes.
It is a required companion to the source adaptation notices and the provenance in [docs/root-fork.md](root-fork.md).

**Authorship and license:** the changes derive from upstream Mika, its `feat/root-mode` work, and the upstream
pull requests identified in `docs/root-fork.md`. This fork does not claim sole authorship and does not relicense
upstream or third-party work. Original copyright and license notices are preserved. `LICENSE` and `NOTICE` were
not edited.

## Notice policy

Kotlin and XML files received a two-line/two-line-equivalent comment immediately after the package or
`<resources>` boundary. Comments do not alter Kotlin or Android resource runtime content. The exact notices
were stripped and compared byte-for-byte with the pre-notice snapshots; result: PASS. The coordinator's
preflight evidence records the before/after hashes and strips only the inserted comment blocks.`

Prompt Markdown, prompt-surface/golden text fixtures, documentation and deleted paths were **not** given inline
comments where that would alter prompt/runtime text or where the path no longer exists. Their treatment is stated
in the table below. Generated code/large binary/runtime assets are not modified here; their source obligations are
covered by the corresponding-source archive and the third-party notices.

## File-by-file manifest

| Path | Git state | Notice/treatment |
|---|---|---|
| `.gitignore` | ` M` | Ignore rules only |
| `README.md` | ` M` | Documentation/prompt text; no inline source notice |
| `app/src/main/java/com/wangzan101/mika/about/Diagnostics.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/about/DiagnosticsCollector.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/cap/phone/setup/BuiltinShellManager.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/cap/phone/setup/RootControl.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/cap/phone/setup/RootShell.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/cap/root/RootCommands.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/confirm/ConfirmationNotifier.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/selfcheck/AndroidSelfCheck.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/settings/Prerequisites.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/settings/ProviderPresets.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/settings/ReasoningOptions.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/settings/SystemStatusMonitor.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/task/TaskRun.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/ui/ChatViewModel.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/ui/PhoneSetupCopy.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/ui/PhoneSetupScreen.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/ui/RecoverySection.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/ui/RootSection.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/ui/SettingsSubPages.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/java/com/wangzan101/mika/ui/StatusCopy.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-b+zh+Hant/strings_confirm.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-b+zh+Hant/strings_phone.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-b+zh+Hant/strings_recovery.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-b+zh+Hant/strings_tools.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-es/strings_confirm.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-es/strings_phone.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-es/strings_recovery.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-es/strings_tools.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-in/strings_confirm.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-in/strings_phone.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-in/strings_recovery.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-in/strings_tools.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ja/strings_confirm.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ja/strings_phone.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ja/strings_recovery.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ja/strings_tools.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ko/strings_confirm.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ko/strings_phone.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ko/strings_recovery.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ko/strings_tools.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-pt/strings_confirm.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-pt/strings_phone.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-pt/strings_recovery.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-pt/strings_tools.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ru/strings_confirm.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ru/strings_phone.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ru/strings_recovery.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-ru/strings_tools.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-zh/strings_confirm.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-zh/strings_phone.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-zh/strings_recovery.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values-zh/strings_tools.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values/strings_confirm.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values/strings_phone.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values/strings_recovery.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/main/res/values/strings_tools.xml` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/test/java/com/wangzan101/mika/cap/phone/setup/RootControlTest.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `app/src/test/java/com/wangzan101/mika/cap/phone/setup/RootShellTest.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `app/src/test/java/com/wangzan101/mika/cap/root/FakeRootProcess.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `app/src/test/java/com/wangzan101/mika/cap/root/RootCommandsTest.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `app/src/test/java/com/wangzan101/mika/confirm/ConfirmationCopyTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/test/java/com/wangzan101/mika/settings/ProviderPresetsTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/test/java/com/wangzan101/mika/settings/ReasoningSettingsTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `app/src/test/java/com/wangzan101/mika/ui/PhoneSetupCopyTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/action/ActionExecutor.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/action/ActionOutcome.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/action/Approval.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/action/ConfirmDecision.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/action/FollowUp.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/agent/AgentLoop.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/assembly/TaskTools.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/cap/mcp/HttpMcpClient.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/cap/phone/CommandSteer.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/cap/phone/PhoneAdvancedTools.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/cap/root/RootCapability.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/cap/root/RootGroup.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| core/src/main/kotlin/com/wangzan101/mika/core/cap/sandbox/OutputClip.kt | ` D` | Deleted/moved path; no in-file notice; replacement path covered |
| `core/src/main/kotlin/com/wangzan101/mika/core/cap/sandbox/SandboxTools.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/child/ChildRuns.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/gate/ConfirmationGate.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/remote/HttpServer.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/shell/BuiltinShell.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/shell/BuiltinShellEngine.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/shell/HelperHandoff.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/shell/PhoneSetupFlow.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/shell/RootAccess.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/tool/AgentTool.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/tool/OutputClip.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/kotlin/com/wangzan101/mika/core/tool/ToolDispatcher.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/main/resources/prompts/capabilities/root.md` | `??` | Documentation/prompt text; no inline source notice |
| `core/src/main/resources/prompts/feedback/tools.md` | ` M` | Documentation/prompt text; no inline source notice |
| `core/src/main/resources/prompts/tools/root_run_command.md` | `??` | Documentation/prompt text; no inline source notice |
| `core/src/test/kotlin/com/wangzan101/mika/core/action/ApprovalPolicyTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/assembly/RootAssemblyTest.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/cap/mcp/HttpMcpEndpointTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/cap/phone/CommandSteerTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/cap/root/RootApprovalExecutionTest.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/cap/root/RootCommandToolTest.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| core/src/test/kotlin/com/wangzan101/mika/core/cap/sandbox/OutputClipTest.kt | ` D` | Deleted/moved path; no in-file notice; replacement path covered |
| `core/src/test/kotlin/com/wangzan101/mika/core/child/ChildRunsTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/gate/ConfirmationGateTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/prompt/PromptLayoutTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/remote/HttpServerTest.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/shell/BuiltinShellEngineTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/shell/PhoneSetupFlowTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/shell/RootAccessTest.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/tool/OutputClipTest.kt` | `??` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/kotlin/com/wangzan101/mika/core/tool/ToolRiskTest.kt` | ` M` | Source notice added; strip proof restores accepted bytes |
| `core/src/test/resources/prompt-surface/sizes.txt` | ` M` | Golden/prompt fixture text; no comment inserted |
| `docs/dev/architecture.md` | ` M` | Documentation; no source notice |
| `docs/fork-modifications.md` | `M` | This manifest; no source notice |
| `docs/modes.en.md` | ` M` | Documentation; no source notice |
| `docs/modes.md` | ` M` | Documentation; no source notice |
| `docs/readme/README.es.md` | ` M` | Documentation/prompt text; no inline source notice |
| `docs/readme/README.ja.md` | ` M` | Documentation/prompt text; no inline source notice |
| `docs/readme/README.ko.md` | ` M` | Documentation/prompt text; no inline source notice |
| `docs/readme/README.zh-CN.md` | ` M` | Documentation/prompt text; no inline source notice |
| `docs/readme/README.zh-Hant.md` | ` M` | Documentation/prompt text; no inline source notice |
| `docs/root-fork.md` | `??` | Documentation/prompt text; no inline source notice |
| `docs/root-fork.zh-CN.md` | `??` | Documentation/prompt text; no inline source notice |
| `docs/setup-guide.en.md` | ` M` | Documentation; no source notice |
| `docs/setup-guide.md` | ` M` | Documentation; no source notice |

## Notes

- The table covers accepted changed, new and deleted paths, not only the ROOT classes.
- Source notices were added only to changed/new Kotlin and XML files. The exact comment block is removed by the
  verifier without any other byte change.
- Prompt and golden `.md`/`.txt` files retain their accepted text; no comment was inserted because those bytes are
  runtime/test content.
- Deleted paths listed as `D` are intentional moves/removals in the accepted tree; the replacement path is in the
  same manifest.
- The corresponding-source archive is a separate local artifact and is not itself a source-code modification.
  It is prepared locally but not uploaded or hosted here; its size/hash and contents are recorded in
  [root-fork.md](root-fork.md).
