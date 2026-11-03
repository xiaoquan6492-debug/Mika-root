package com.wangzan101.mika.core.cap.root
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import com.wangzan101.mika.core.action.ActionRisk
import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.tool.FakeScreen
import com.wangzan101.mika.core.tool.Verb
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCommandToolTest {
    private class FakePort : RootPort {
        override suspend fun run(command: String, timeoutMs: Long) = RootRun.Unavailable
    }

    @Test
    fun registeredOnlyWithAPort() {
        assertTrue(toolDispatcher(FakeScreen()).offeredNow(all = true).none { it.name == "root_run_command" })
        val with = toolDispatcher(FakeScreen(), root = FakePort())
        assertTrue(with.offeredNow().any { it.name == "root_run_command" })
    }

    @Test
    fun approvalKeepsTheRawCommandIdentity() {
        val raw = "  echo 'x'  "
        val risk = RootRunCommand.approvalRisk(JsonObject(mapOf("command" to JsonPrimitive(raw))))
        assertEquals(raw, risk.ask!!.content)
        val trimmed = raw.trim()
        assertTrue(raw != trimmed)
        val gate = com.wangzan101.mika.core.gate.ConfirmationGate()
        gate.declare(risk.ask!!, ROOT_CONFIRM_APP)
        gate.decide(true, 1)
        assertTrue(gate.permitsExactly(risk.ask!!.copy(content = trimmed), 2) is com.wangzan101.mika.core.gate.Permit.Mismatch)
    }

    @Test
    fun everyCommandDeclaresAnExactOneShotAsk() {
        for (command in listOf("ls -l /data", "rm /sdcard/Download/old.apk", "pm list packages")) {
            val risk = RootRunCommand.approvalRisk(JsonObject(mapOf("command" to JsonPrimitive(command))))
            assertEquals(ActionRisk.Irreversible, risk.level)
            assertNotNull(risk.ask)
            assertEquals(command, risk.ask!!.content)
            assertEquals(ROOT_CONFIRM_APP, risk.ask!!.app)
        }
        assertEquals(Verb.RootCommand, RootRunCommand.verb)
        assertEquals("root_run_command", RootRunCommand.name)
        assertEquals("root_run_command", RootRunCommand.def.name)
    }
}
