package com.wangzan101.mika.cap.root
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import com.wangzan101.mika.core.cap.root.RootRun
import com.wangzan101.mika.core.shell.SuAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class RootCommandsTest {
    private val marker = "__mika_root_ok__"

    private fun finished(output: String = "ok\n"): FakeRootProcess =
        FakeRootProcess(ByteArrayInputStream("$marker\n$output".toByteArray()), initiallyExited = true)

    @Test
    fun markerAndDataSurviveAnOpenPipeWithoutRefusing() = runBlocking {
        val pipe = PipedInputStream(64 * 1024)
        val writer = PipedOutputStream(pipe)
        val p = FakeRootProcess(pipe, initiallyExited = true)
        writer.write("$marker\ndone\n".toByteArray())
        writer.flush()
        val refusals = mutableListOf<SuAnswer?>()
        val c = RootCommands({ "/fake/su" }, { true }, { refusals += it }, start = { p }, clock = { 1_000 })

        val r = c.run("echo done", 1_000)
        assertTrue(r is RootRun.Done)
        assertEquals("done\n", (r as RootRun.Done).stdout)
        assertTrue("refused $refusals", refusals.isEmpty())
        assertTrue(p.wasInputClosed)
        runCatching { writer.close() }
        Unit
    }

    @Test
    fun timeoutWithNoMarkerIsNoAnswerAndForcesTheDirectProcess() = runBlocking {
        val p = FakeRootProcess(immediateWaitTimeout = true)
        val refusals = mutableListOf<SuAnswer?>()
        val c = RootCommands({ "/fake/su" }, { true }, { refusals += it }, start = { p }, clock = { 1_000 })

        val r = c.run("sleep 20", 20)
        assertTrue(r is RootRun.NoAnswer)
        assertEquals(listOf(SuAnswer.NoAnswer), refusals)
        assertTrue(p.forciblyDestroyed)
        assertTrue(p.wasInputClosed)
    }

    @Test
    fun launchScriptBase64RoundTripsExactCommandBytes() {
        val c = RootCommands({ "/fake/su" }, { true }, {})
        for (command in listOf(
            "printf '<%s>' hi\\\n",
            "printf '<%s>' hi\\\n\n",
            "echo 'a b :·' \"c\\\\d\"\n",
            "printf 'λ 🙃'\n",
        )) {
            val script = c.script(command, 1_000)
            val encoded = Regex("echo '([^']+)'").find(script)!!.groupValues[1]
            assertArrayEquals(command.toByteArray(), Base64.getDecoder().decode(encoded))
        }
    }

    private fun host(command: String): String {
        // 写文件再 `sh file`：避免 Windows ProcessBuilder 对复杂引号参数的命令行转义;
        // 关闭 stdin,让 cat/read 看到 EOF,和 direct sh -c + closed stdin 一致。
        val file = Files.createTempFile("mika-host-", ".sh")
        return try {
            Files.write(file, command.toByteArray(StandardCharsets.UTF_8))
            val p = ProcessBuilder("sh", file.toString()).redirectErrorStream(true).start()
            p.outputStream.close()
            val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                error("host sh timed out")
            }
            out
        } finally {
            runCatching { Files.deleteIfExists(file) }
        }
    }

    @Test
    fun hostShMatchesDirectShCWithClosedStdinForExactBytes() {
        val hostAvailable = runCatching {
            ProcessBuilder("sh", "-c", "command -v base64 >/dev/null 2>&1").start().waitFor() == 0
        }.getOrDefault(false)
        org.junit.Assume.assumeTrue("host sh/base64 unavailable", hostAvailable)
        val c = RootCommands({ "/fake/su" }, { true }, {})
        val commands = listOf(
            "printf '<%s>' hi\\\n",
            "printf '<%s>' hi\n\n",
            "cat\nprintf 'AFTER\\n'\n",
            "read x\nprintf 'AFTER\\n'\n",
            "printf '%s' 'a b :· λ'\n",
            "echo 'quoted \"spaces\" and \\slashes'\n",
        )
        for (command in commands) {
            val expected = host(command)
            val generated = c.script(command, 1_000)
            val actual = host(generated).removePrefix("$marker\n")
            assertEquals(command, expected, actual)
        }
    }

    @Test
    fun nulCommandIsRejectedBeforeStartingSu() = runBlocking {
        var starts = 0
        val c = RootCommands({ "/fake/su" }, { true }, {}, start = { starts++; finished() }, clock = { 0 })
        assertTrue(c.run("echo a\u0000b", 1_000) is RootRun.Invalid)
        assertEquals(0, starts)
    }

    @Test
    fun markerPresentButProcessNotExitedReturnsDoneTimedOut() = runBlocking {
        val p = FakeRootProcess(
            ByteArrayInputStream("$marker\ndone\n".toByteArray()),
            initiallyExited = false,
            immediateWaitTimeout = true,
        )
        val refusals = mutableListOf<SuAnswer?>()
        val c = RootCommands({ "/fake/su" }, { true }, { refusals += it }, start = { p }, clock = { 1_000 })
        val r = c.run("slow", 20) as RootRun.Done
        assertTrue(r.timedOut)
        assertEquals("done\n", r.stdout)
        assertTrue(refusals.isEmpty())
        assertTrue(p.forciblyDestroyed)
        assertTrue(p.wasInputClosed)
    }

    @Test
    fun stdoutAndStderrAreBoundedWithExactDropCounts() = runBlocking {
        val limit = 1 shl 20
        val out = "$marker\n" + "a".repeat(limit + 10)
        val err = "e".repeat(limit + 20)
        val p = FakeRootProcess(
            ByteArrayInputStream(out.toByteArray()),
            initiallyExited = true,
            errorStream = ByteArrayInputStream(err.toByteArray()),
        )
        val c = RootCommands({ "/fake/su" }, { true }, {}, start = { p }, clock = { 900 })
        val r = c.run("id", 1_000) as RootRun.Done
        assertEquals(limit - marker.length - 1, r.stdout.length)
        assertEquals(limit, r.stderr.length)
        assertEquals(marker.length + 1 + 10 + 20L, r.droppedBytes)
        assertFalse(r.stdout.startsWith(marker))
    }

    @Test
    fun anAlreadyCreatedInstanceRefusesAfterTheSwitchIsTurnedOff() = runBlocking {
        var allowed = true
        var starts = 0
        val c = RootCommands({ "/fake/su" }, { allowed }, {}, start = { starts++; finished() }, clock = { 0 })
        allowed = false
        val r = c.run("id", 1_000)
        assertTrue(r is RootRun.Disabled)
        assertEquals(0, starts)
    }

    @Test
    fun revokedWhileWaitingForTheLockIsCheckedAgain() = runBlocking {
        val pipe = PipedInputStream(64 * 1024)
        val writer = PipedOutputStream(pipe)
        val first = FakeRootProcess(pipe)
        val starts = AtomicInteger()
        var allowed = true
        val c = RootCommands(
            { "/fake/su" }, { allowed }, {},
            start = { starts.incrementAndGet(); if (starts.get() == 1) first else finished("second\n") },
            clock = { 0 },
        )
        val a = async(Dispatchers.Default) { c.run("first", 30_000) }
        while (starts.get() == 0) delay(5)
        val second = async(Dispatchers.Default) { c.run("second", 30_000) }
        delay(100)
        allowed = false
        writer.write("$marker\nfirst\n".toByteArray())
        writer.flush()
        runCatching { writer.close() }
        first.finish()
        assertTrue(a.await() is RootRun.Done)
        assertTrue(second.await() is RootRun.Disabled)
        assertEquals(1, starts.get())
    }

    @Test
    fun cancellingARunningCommandDestroysTheDirectProcess() = runBlocking {
        val p = FakeRootProcess()
        val c = RootCommands({ "/fake/su" }, { true }, {}, start = { p }, clock = { 0 })
        val job = async(Dispatchers.Default) { c.run("sleep 20", 30_000) }
        delay(100)
        job.cancelAndJoin()
        assertTrue(p.forciblyDestroyed)
        assertTrue(p.wasInputClosed)
    }

    @Test
    fun commandsAreGloballySerialAndWaitingCanBeCancelled() = runBlocking {
        val pipe = PipedInputStream(64 * 1024)
        val writer = PipedOutputStream(pipe)
        val first = FakeRootProcess(pipe)
        val starts = AtomicInteger()
        val c = RootCommands(
            { "/fake/su" }, { true }, {},
            start = { if (starts.incrementAndGet() == 1) first else finished("second\n") },
            clock = { 0 },
        )
        val a = async(Dispatchers.Default) { c.run("first", 30_000) }
        while (starts.get() == 0) delay(5)
        val second = async(Dispatchers.Default) { c.run("second", 30_000) }
        delay(100)
        assertEquals("must wait for the first command", 1, starts.get())
        val cancelled = withTimeoutOrNull(50) { c.run("third", 30_000) }
        assertEquals(null, cancelled)
        assertEquals("cancelled waiter must not start a process", 1, starts.get())

        writer.write("$marker\nfirst\n".toByteArray())
        writer.flush()
        runCatching { writer.close() }
        first.finish()
        assertTrue(a.await() is RootRun.Done)
        assertTrue(second.await() is RootRun.Done)
        assertEquals(2, starts.get())
    }
}
