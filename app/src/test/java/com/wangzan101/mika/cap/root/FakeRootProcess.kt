package com.wangzan101.mika.cap.root
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 测试用 direct su:输出流可阻塞,normal destroy 可被忽略,便于验证强杀和有界清理。 */
internal class FakeRootProcess(
    stream: InputStream = BlockingInput(),
    initiallyExited: Boolean = false,
    private val ignoreNormalDestroy: Boolean = true,
    private val immediateWaitTimeout: Boolean = false,
    private val errorStream: InputStream = ByteArrayInputStream(ByteArray(0)),
) : Process() {
    private val exited = CountDownLatch(if (initiallyExited) 0 else 1)
    @Volatile private var inputClosed = false
    @Volatile var normalDestroyed = false
        private set
    @Volatile var forciblyDestroyed = false
        private set

    private val input = object : FilterInputStream(stream) {
        override fun close() {
            inputClosed = true
            runCatching { super.close() }
        }
    }
    private val output = ByteArrayOutputStream()

    val wasInputClosed: Boolean get() = inputClosed

    fun finish() = exited.countDown()

    override fun getOutputStream(): ByteArrayOutputStream = output
    override fun getInputStream(): InputStream = input
    override fun getErrorStream(): InputStream = errorStream

    override fun waitFor(): Int {
        exited.await()
        return 0
    }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
        if (immediateWaitTimeout && isAlive) false else exited.await(timeout, unit)

    override fun exitValue(): Int {
        if (isAlive) throw IllegalThreadStateException()
        return 0
    }

    override fun destroy() {
        normalDestroyed = true
        if (!ignoreNormalDestroy) finish()
    }

    override fun destroyForcibly(): Process {
        forciblyDestroyed = true
        finish()
        return this
    }

    override fun isAlive(): Boolean = exited.count > 0
}

/** 没有数据、只有 close 才醒的输入流。 */
internal class BlockingInput : InputStream() {
    private val open = CountDownLatch(1)
    override fun read(): Int {
        open.await()
        return -1
    }
    override fun read(b: ByteArray, off: Int, len: Int): Int = read()
    override fun close() { open.countDown() }
}
