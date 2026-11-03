package com.wangzan101.mika.core.assembly
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import com.wangzan101.mika.core.cap.root.RootPort
import com.wangzan101.mika.core.cap.root.RootRun
import com.wangzan101.mika.core.run.Attendance
import com.wangzan101.mika.core.run.RunId
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.core.run.RunProvenance
import com.wangzan101.mika.core.run.RunSpec
import com.wangzan101.mika.core.cap.web.WebPort
import com.wangzan101.mika.core.tool.FakeScreen
import com.wangzan101.mika.core.cap.web.FakeWebPort
import com.wangzan101.mika.core.tool.ToolDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootAssemblyTest {
    private class FakePort : RootPort {
        override suspend fun run(command: String, timeoutMs: Long) = RootRun.Unavailable
    }

    private fun spec(
        origin: RunOrigin = RunOrigin.Ui,
        attendance: Attendance = Attendance.Present,
        readOnly: Boolean = false,
        parent: RunId? = null,
        capabilities: Set<String>? = null,
    ) = RunSpec(
        RunId("root-assembly"), "root", "thread",
        provenance = RunProvenance(origin, attendance), readOnly = readOnly, parent = parent, capabilities = capabilities,
    )

    private fun dispatcher(spec: RunSpec, web: WebPort? = null): ToolDispatcher = toolDispatcher(FakeScreen(), web = web, root = FakePort(), runSpec = spec)

    private fun exposesRoot(d: ToolDispatcher) =
        "root" in d.capabilityIds() && d.offeredNow(all = true).any { it.name == "root_run_command" }

    @Test
    fun attendedUiWithRawCapabilitiesExposesRoot() {
        assertTrue(exposesRoot(dispatcher(spec())))
        assertTrue(exposesRoot(dispatcher(spec(capabilities = setOf("root", "phone")))))
    }

    @Test
    fun sourceAndParentAndReadOnlyRunsNeverExposeRoot() {
        for (s in listOf(
            spec(origin = RunOrigin.Channel),
            spec(origin = RunOrigin.Trigger),
            spec(origin = RunOrigin.Mcp),
            spec(attendance = Attendance.Reachable),
            spec(attendance = Attendance.Unattended),
            spec(readOnly = true),
            spec(parent = RunId("parent")),
            spec(capabilities = setOf("phone")),
        )) {
            val d = dispatcher(s)
            assertFalse("$s", exposesRoot(d))
            assertFalse("$s", d.offeredNow(all = true).any { it.name == "root_run_command" })
        }
        // 子任务只开 web/sandbox 能力;root 仍不能出现
        val child = dispatcher(spec(origin = RunOrigin.Child, capabilities = setOf("web")), web = FakeWebPort())
        assertFalse(exposesRoot(child))
    }

    @Test
    fun noPortKeepsRootUnknown() {
        val d = toolDispatcher(FakeScreen(), runSpec = spec())
        assertFalse("root" in d.capabilityIds())
        assertFalse(d.offeredNow(all = true).any { it.name == "root_run_command" })
    }
}
