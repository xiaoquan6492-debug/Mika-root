package com.wangzan101.mika.cap.phone.setup
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import com.wangzan101.mika.core.shell.SuAnswer
import com.wangzan101.mika.core.shell.SuProbe
import java.io.File
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 手机上的 su:找它、问它、经它跑进程。只按能力判断(找得到 su 文件、跑出来的 uid),不认是哪种 root。
 * 所有方法都会阻塞(su 可能正弹着授权框等用户),只在 IO 线程上调。
 */
object RootShell {
    /** su 的完整路径;找不到为 null。 */
    fun su(): String? = SuProbe.find(System.getenv("PATH")) { runCatching { File(it).exists() }.getOrDefault(false) }

    /**
     * 问 su 一次:以 [uid](0 是 root,2000 是 shell)跑 `id`,最多等 [timeoutMs](第一次会弹授权框,要给用户时间)。
     * 找不到 su 为 null。
     */
    fun ask(uid: Int, timeoutMs: Long): SuAnswer? = su()?.let { ask(it, uid, timeoutMs) } ?: null

    /** [ask] 的可测入口:不查 PATH,直接拿 [su] 路径和 [start] 起进程。 */
    internal fun ask(su: String, uid: Int, timeoutMs: Long, start: (List<String>) -> Process = ::start): SuAnswer? {
        val args = if (uid == 0) SuProbe.rootArgs(su, "id") else SuProbe.shellArgs(su, "id")
        val p = runCatching { start(args) }.getOrElse { return SuAnswer.Denied }
        var out: CompletableFuture<String>? = null
        try {
            out = collect(p.inputStream)
            val exited = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            val text = if (exited) runCatching { out.get(1, TimeUnit.SECONDS) }.getOrDefault("") else ""
            return SuProbe.answer(text, timedOut = !exited, expectUid = uid)
        } finally {
            terminate(p)
            out?.cancel(true)
        }
    }

    /** 起一个 su 进程;标准错误并进标准输出(拒绝时 su 的说明在那里)。 */
    fun start(args: List<String>): Process = ProcessBuilder(args).redirectErrorStream(true).start()

    /**
     * 读进程输出,直到读到一行 uid(返回它)、流结束(null:su 没跑命令就退出了)或到 [timeoutMs](抛 java.util.concurrent.TimeoutException)。
     * 超时/中断/读失败都会先关掉读的那条流,让读线程退出;进程由调用方或 [terminate] 结束。
     */
    fun readUid(p: Process, timeoutMs: Long): Int? {
        val f = onThread {
            val r = p.inputStream.bufferedReader()
            generateSequence { runCatching { r.readLine() }.getOrNull() }.firstNotNullOfOrNull(SuProbe::uid)
        }
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            f.cancel(true)
            runCatching { p.inputStream.close() }
            throw e
        } catch (e: InterruptedException) {
            f.cancel(true)
            runCatching { p.inputStream.close() }
            Thread.currentThread().interrupt()
            throw e
        } catch (e: ExecutionException) {
            f.cancel(true)
            runCatching { p.inputStream.close() }
            throw e
        }
    }

    /**
     * 结束一条 direct su 并关掉所有流:先正常 destroy,等一小会儿;不退出就强杀,再等一小会儿。
     * 这里的 direct 进程由 Mika 拥有;su 再派生的孙进程不保证被一起清理。
     */
    fun terminate(p: Process) {
        runCatching { p.outputStream.close() }
        runCatching { p.inputStream.close() }
        runCatching { p.errorStream.close() }
        runCatching { p.destroy() }
        val stopped = runCatching { p.waitFor(GRACE_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!stopped) {
            runCatching { p.destroyForcibly() }
            runCatching { p.waitFor(GRACE_MS, TimeUnit.MILLISECONDS) }
        }
    }

    /** 在单独的线程上做阻塞的读(不占公共线程池)。 */
    fun <T> onThread(block: () -> T): CompletableFuture<T> = CompletableFuture<T>().also { f ->
        Thread({ runCatching(block).fold({ f.complete(it) }, { f.completeExceptionally(it) }) }, "mika-su").apply { isDaemon = true }.start()
    }

    /** 在另一条线程上把 [stream] 读完(最多 [max] 字节,多的丢掉)。 */
    fun collect(stream: InputStream, max: Int = 16 * 1024): CompletableFuture<String> = onThread {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        runCatching {
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                if (buf.size() < max) buf.write(chunk, 0, minOf(n, max - buf.size()))
            }
        }
        buf.toString(Charsets.UTF_8.name())
    }

    private const val GRACE_MS = 1_000L
}
