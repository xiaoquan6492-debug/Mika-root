package com.wangzan101.mika.cap.phone.setup
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import com.wangzan101.mika.cap.root.FakeRootProcess
import com.wangzan101.mika.core.shell.SuAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeoutException

class RootShellTest {
    @Test
    fun readUidTimeoutClosesTheReaderAndAllowsBoundedTermination() {
        val p = FakeRootProcess(immediateWaitTimeout = true)
        assertThrows(TimeoutException::class.java) { RootShell.readUid(p, 20) }
        assertTrue(p.wasInputClosed)

        RootShell.terminate(p)
        assertTrue(p.normalDestroyed)
        assertTrue(p.forciblyDestroyed)
        assertTrue(p.wasInputClosed)
    }

    @Test
    fun interruptingReadUidClosesTheReader() {
        val p = FakeRootProcess(immediateWaitTimeout = true)
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedException::class.java) { RootShell.readUid(p, 1_000) }
            assertTrue(p.wasInputClosed)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun askTimeoutTerminatesTheDirectSuAndDoesNotReportAGrant() {
        val p = FakeRootProcess(immediateWaitTimeout = true)
        val answer = RootShell.ask("/fake/su", 0, 20) { p }
        assertEquals(SuAnswer.NoAnswer, answer)
        assertTrue(p.forciblyDestroyed)
        assertTrue(p.wasInputClosed)
    }
}
