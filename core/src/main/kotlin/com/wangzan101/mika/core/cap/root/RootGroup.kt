package com.wangzan101.mika.core.cap.root
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import com.wangzan101.mika.core.action.ActionRisk
import com.wangzan101.mika.core.action.Risk as ApprovalRisk
import com.wangzan101.mika.core.gate.ConfirmationRequest
import com.wangzan101.mika.core.observation.ObsKind
import com.wangzan101.mika.core.observation.Observation
import com.wangzan101.mika.core.observation.Place
import com.wangzan101.mika.core.observation.Trust
import com.wangzan101.mika.core.observation.Untrusted
import com.wangzan101.mika.core.tool.OutputClip
import com.wangzan101.mika.core.prompt.Feedback
import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.risk.IrrevAction
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.Args
import com.wangzan101.mika.core.tool.Risk
import com.wangzan101.mika.core.tool.ToolContext
import com.wangzan101.mika.core.tool.ToolDef
import com.wangzan101.mika.core.tool.ToolExecution
import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.tool.argStr
import com.wangzan101.mika.core.tool.int
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** root 命令申报的「在哪里」:确认卡片据此显示成 root 命令,而不是某个 App。 */
const val ROOT_CONFIRM_APP = "root"

/** 命令最长多少字;确认卡片上整条显示。 */
private const val COMMAND_MAX = 4_000
private const val DEFAULT_TIMEOUT_SEC = 30
private const val MAX_TIMEOUT_SEC = 300

/**
 * 以 root 跑一条 Android shell 命令(经 su,uid 0),返回退出码、标准输出与标准错误(长的留头尾)。
 * 本轮保守审批:**询问模式下每条命令(包括只读)都先给用户看完整命令,同意了才跑**;
 * 自主模式只在有人 UI 任务里执行,无人值守/非 UI/子任务/条件检查由端口层拒绝。
 * 不动屏幕,进动作清单。
 */
internal object RootRunCommand : AgentTool {
    override val capability: String get() = ROOT
    override val verb = Verb.RootCommand
    override val label = "root 命令"
    override val risk = Risk.Write
    override val offScreen = true
    override val logged = true
    override val wideTarget = true
    override val def = ToolDef("root_run_command", Prompts.tool("root_run_command"), mapOf(
        "command" to buildJsonObject {
            put("type", "string"); put("description", "The Android shell command to run as root, 1-$COMMAND_MAX characters.")
            put("minLength", 1); put("maxLength", COMMAND_MAX)
        },
        "timeoutSec" to int("Time limit in seconds, 1-$MAX_TIMEOUT_SEC (default $DEFAULT_TIMEOUT_SEC). The command is stopped when it runs out.", 1, MAX_TIMEOUT_SEC),
    ), listOf("command"))

    override fun available(ctx: ToolContext) = ctx.rootPort != null

    /** 每条命令都按完整命令声明一次不可逆审批;只读也不再豁免。 */
    override fun approvalRisk(args: JsonObject): ApprovalRisk {
        val command = (args["command"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return ApprovalRisk(ActionRisk.Irreversible, ask = ask(command))
    }

    /** 给用户看的申报:就是这条命令。 */
    fun ask(command: String) = ConfirmationRequest(IrrevAction.other, "", command, null, ROOT_CONFIRM_APP, oneShot = true)

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val command = a.str("command", max = COMMAND_MAX)!!
        // argv/shell 脚本流都不能表示 NUL;明确拒绝,而不是让底层截断成另一条命令
        if ('\u0000' in command) return fail("invalid_command", "Root command contains NUL (\\u0000), which cannot be executed safely.")
        val timeoutSec = a.int("timeoutSec", 1, MAX_TIMEOUT_SEC) ?: DEFAULT_TIMEOUT_SEC
        return when (val r = ctx.rootPort!!.run(command, timeoutSec * 1000L)) {
            is RootRun.Done -> done(r, command)
            RootRun.Disabled -> fail("root_disabled", Feedback["root.disabled"])
            RootRun.Denied -> fail("root_denied", Feedback["root.denied"])
            RootRun.NoAnswer -> fail("root_no_answer", Feedback["root.no_answer"])
            RootRun.Unavailable -> fail("root_unavailable", Feedback["root.unavailable"])
            RootRun.Invalid -> fail("invalid_command", "Root command contains NUL (\u0000), which cannot be executed safely.")
        }
    }

    /**
     * 跑完的结果。输出(失败的也是事实)可能被回答引用,记成一条观察:命令是助手自己写的,输出里原样的回显不算读到的;
     * 读的多是别的 App 的数据,只当数据。
     */
    private fun done(r: RootRun.Done, command: String): ToolExecution {
        val (out, outClipped) = OutputClip.clip(r.stdout)
        val (err, errClipped) = OutputClip.clip(r.stderr, head = 1_000, tail = 3_000)
        val hints = listOfNotNull(
            Feedback["root.timed_out"].takeIf { r.timedOut },
            Feedback["root.truncated"].takeIf { outClipped || errClipped || r.droppedBytes > 0 },
        )
        return ToolExecution(buildJsonObject {
            put("ok", r.exitCode == 0 && !r.timedOut); put("exitCode", r.exitCode); put("timedOut", r.timedOut); put("durationMs", r.durationMs)
            put("stdout", Untrusted.wrap(out)); put("stderr", Untrusted.wrap(err))
            if (r.droppedBytes > 0) put("droppedBytes", r.droppedBytes)
            if (hints.isNotEmpty()) put("hint", hints.joinToString(" "))
        }, observations = listOf(Observation(ObsKind.Output, "退出码 ${r.exitCode}:\n$out", source = ROOT,
            place = Place.command("root_run_command「${command.take(200)}」"), trust = Trust.Untrusted, authored = command)))
    }

    private fun fail(error: String, message: String) = ToolExecution(buildJsonObject {
        put("ok", false); put("error", error); put("message", message); put("recoverable", false)
    })

    override fun logTarget(o: JsonObject, r: ToolExecution, ctx: ToolContext) = o.argStr("command")
}

internal object RootGroup {
    val tools: List<AgentTool> = listOf(RootRunCommand)
}
