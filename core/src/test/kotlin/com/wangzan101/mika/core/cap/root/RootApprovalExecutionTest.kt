package com.wangzan101.mika.core.cap.root
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import com.wangzan101.mika.core.agent.AgentLoop
import com.wangzan101.mika.core.agent.FakeModel
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.model.StopReason
import com.wangzan101.mika.core.testing.answerReply
import com.wangzan101.mika.core.tool.FakeScreen
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootApprovalExecutionTest {
    private class RecordingPort : RootPort {
        val commands = mutableListOf<String>()
        override suspend fun run(command: String, timeoutMs: Long): RootRun {
            commands += command
            return RootRun.Done(0, false, "ok", "", 0, 1)
        }
    }

    private var seq = 0
    private fun call(command: String) =
        Part.ToolCall("root${++seq}", "root_run_command", """{"command":"$command"}""")

    private fun tools(vararg calls: Part.ToolCall) = ModelReply(calls.toList(), StopReason.ToolUse, null)

    @Test
    fun askRunsOnlyTheApprovedExactCommandAndAsksAgain() = runTest {
        val port = RecordingPort()
        val m = FakeModel(
            tools(call("ls -l /data")),
            tools(call("ls -l /data")),
            answerReply("done"),
        )
        val loop = AgentLoop(m, toolDispatcher(FakeScreen(), root = port), "BASE")
        val run = async { loop.run("read data with root") }
        advanceUntilIdle()
        assertTrue(loop.state.value is TaskState.WaitingConfirmation)
        assertTrue(port.commands.isEmpty())

        loop.answerConfirmation(true)
        advanceUntilIdle()
        assertEquals(listOf("ls -l /data"), port.commands)
        // 同一条命令的第二次调用仍需新同意:旧同意一次性
        assertTrue(loop.state.value is TaskState.WaitingConfirmation)

        loop.answerConfirmation(false)
        advanceUntilIdle()
        run.await()
        assertEquals(listOf("ls -l /data"), port.commands)
    }

    @Test
    fun autonomousAttendedUiRunsWithoutAPrompt() = runTest {
        val port = RecordingPort()
        val m = FakeModel(tools(call("id -u")), answerReply("done"))
        val loop = AgentLoop(m, toolDispatcher(FakeScreen(), root = port), "BASE", autonomous = true)
        loop.run("run root")
        assertEquals(listOf("id -u"), port.commands)
    }

    @Test
    fun deniedAskNeverCallsThePort() = runTest {
        val port = RecordingPort()
        val m = FakeModel(tools(call("rm /sdcard/Download/old.apk")), answerReply("not done"))
        val loop = AgentLoop(m, toolDispatcher(FakeScreen(), root = port), "BASE")
        val run = async { loop.run("delete with root") }
        advanceUntilIdle()
        assertTrue(loop.state.value is TaskState.WaitingConfirmation)
        loop.answerConfirmation(false)
        advanceUntilIdle()
        run.await()
        assertTrue(port.commands.isEmpty())
    }

    @Test
    fun changingTheCommandCannotReuseAnApproval() = runTest {
        val port = RecordingPort()
        val m = FakeModel(
            tools(call("ls -l /data")),
            tools(call("ls -l /data2")),
            answerReply("stopped"),
        )
        val loop = AgentLoop(m, toolDispatcher(FakeScreen(), root = port), "BASE")
        val run = async { loop.run("read data with root") }
        advanceUntilIdle()
        loop.answerConfirmation(true)
        advanceUntilIdle()
        assertEquals(listOf("ls -l /data"), port.commands)
        // 第二条不同命令必须重新问,不能复用第一条的同意
        assertTrue(loop.state.value is TaskState.WaitingConfirmation)
        loop.answerConfirmation(false)
        advanceUntilIdle()
        run.await()
        assertEquals(listOf("ls -l /data"), port.commands)
    }
}
