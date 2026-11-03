package com.wangzan101.mika.cap.root
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import com.wangzan101.mika.core.cap.root.RootPort
import com.wangzan101.mika.core.cap.root.RootRun
import com.wangzan101.mika.core.shell.SuAnswer
import com.wangzan101.mika.core.shell.SuProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Agent 的 root 命令:经 `su -c` 以 root 跑,标准输出、标准错误分开收(各最多 1 MB),到时限结束。
 * 命令先编成 base64 再在 root 的 sh 里解开交给 `sh -c`:外层那一行总是合法的 sh,命令里的引号、换行原样到达,
 * 写错的命令由里层 sh 报错,和「su 拒绝了」分得开(外层先打一行标记,拿不到标记就是 su 没跑)。
 * 有 toybox 的 timeout 就用它在 root 那边掐断,没有就只靠这一侧到点结束 su。
 *
 * 输出边读边留,不等到 EOF 才交付:命令起了持有管道的后台子进程时,已经读到的标记和输出仍然有效。
 *
 * [su] 给出 su 的完整路径(找不到为 null);su 没跑这条命令时经 [refused] 报出去(null 是 su 不在了),由设置那边记下并关掉这一项。
 * [allowed] 是**实时**判断:开关、su 授权和任务来源(UI/有人)都要在每次执行前重新看,不能只用任务创建时的快照。
 * 跨任务全局串行:同一时间只跑一条 root 命令;等锁可取消,拿到锁后再查一次 [allowed]。
 */
class RootCommands(
    private val su: () -> String?,
    private val allowed: () -> Boolean,
    private val refused: (SuAnswer?) -> Unit,
    private val start: (List<String>) -> Process = { ProcessBuilder(it).start() },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : RootPort {
    override suspend fun run(command: String, timeoutMs: Long): RootRun {
        // NUL 不能进 shell 变量/argv;在检查开关和启动 su 之前就拒绝
        if ('\u0000' in command) return RootRun.Invalid
        if (!allowed()) return RootRun.Disabled
        return SERIAL.withLock {
            // 等锁期间用户可能关掉开关、撤权或来源变化:拿到锁后再看一次
            if (!allowed()) return@withLock RootRun.Disabled
            runInterruptible(Dispatchers.IO) { execute(command, timeoutMs) }
        }
    }

    private fun execute(command: String, timeoutMs: Long): RootRun {
        val su = su() ?: return RootRun.Unavailable.also { refused(null) }
        val started = clock()
        val p = runCatching { start(SuProbe.rootArgs(su, script(command, timeoutMs))) }
            .getOrElse { return RootRun.Denied.also { refused(SuAnswer.Denied) } }
        var out: CompletableFuture<Unit>? = null
        var err: CompletableFuture<Unit>? = null
        try {
            runCatching { p.outputStream.close() }
            val outSink = Sink()
            val errSink = Sink()
            out = onThread { drain(p.inputStream, outSink) }
            err = onThread { drain(p.errorStream, errSink) }
            // 这一侧多等一会儿:root 那边的 timeout 先掐,su 本身卡住(授权框没人点)才由这里结束
            val exited = try {
                p.waitFor(timeoutMs + GRACE_MS, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                throw e
            }
            if (!exited) runCatching { p.destroyForcibly() }
            // 进程已退出时给读线程一点时间把管道里剩余字符收进来;管道被后台子进程握着时也不丢已读部分。
            await(out, if (exited) 2_000 else 1_000)
            await(err, if (exited) 2_000 else 1_000)
            val duration = clock() - started
            val (stdout, outDropped) = outSink.snapshot()
            val (stderr, errDropped) = errSink.snapshot()
            if (!stdout.startsWith(MARK)) {
                // 标记都没打出来:su 没跑这条命令
                val a = if (!exited) SuAnswer.NoAnswer else SuAnswer.Denied
                refused(a)
                return if (a == SuAnswer.NoAnswer) RootRun.NoAnswer else RootRun.Denied
            }
            val exit = if (exited) p.exitValue() else -1
            // timeout -s KILL 掐断时退出码是 137(或 124)
            val timedOut = !exited || (exit == 137 || exit == 124) && duration >= timeoutMs - 500
            return RootRun.Done(exit, timedOut, stdout.removePrefix(MARK).removePrefix("\n"), stderr, outDropped + errDropped, duration)
        } finally {
            out?.cancel(true)
            err?.cancel(true)
            if (p.isAlive) {
                runCatching { p.destroy() }
                if (!runCatching { p.waitFor(1, TimeUnit.SECONDS) }.getOrDefault(false)) {
                    runCatching { p.destroyForcibly() }
                }
            }
            runCatching { p.inputStream.close() }
            runCatching { p.errorStream.close() }
            runCatching { p.outputStream.close() }
        }
    }

    private fun await(f: CompletableFuture<*>, ms: Long) {
        runCatching { f.get(ms, TimeUnit.MILLISECONDS) }
    }

    /**
     * 实际交给 su 的外层脚本。base64 解码后先追加一个固定 sentinel,再交给 `sh -c`:
     * 命令替换只会剥掉末尾换行,而 sentinel 挡住了命令自己的尾随 LF;参数展开再精确去掉这一个 sentinel。
     * 这样既保留任意尾随 LF/反斜杠/Unicode,又保持原 `sh -c` 的 stdin(不是把命令文本接到 stdin)。
     */
    internal fun script(command: String, timeoutMs: Long): String {
        val b64 = Base64.getEncoder().encodeToString(command.toByteArray(Charsets.UTF_8))
        val sec = ((timeoutMs + 999) / 1000).coerceAtLeast(1)
        return "echo $MARK; c=\"\$(echo '$b64' | base64 -d; printf '%s' '$SENTINEL')\"; " +
            "c=\${c%'$SENTINEL'}; " +
            "if command -v timeout >/dev/null 2>&1; then exec timeout -s KILL $sec sh -c \"\$c\"; else exec sh -c \"\$c\"; fi"
    }

    /** 在单独的线程上读(不占公共线程池)。 */
    private fun onThread(block: () -> Unit): CompletableFuture<Unit> = CompletableFuture<Unit>().also { f ->
        Thread({ runCatching(block).fold({ f.complete(Unit) }, { f.completeExceptionally(it) }) }, "mika-root").apply { isDaemon = true }.start()
    }

    /** 边读边留的 1 MB 有界输出。 */
    private class Sink {
        private val buf = ByteArrayOutputStream()
        private var dropped = 0L

        @Synchronized
        fun add(bytes: ByteArray, n: Int) {
            val keep = minOf(n, MAX_BYTES - buf.size())
            if (keep > 0) buf.write(bytes, 0, keep)
            dropped += n - keep
        }

        @Synchronized
        fun snapshot(): Pair<String, Long> = buf.toString(Charsets.UTF_8.name()) to dropped
    }

    /** 读完一个流,最多留 [MAX_BYTES],多的数着丢掉。 */
    private fun drain(stream: InputStream, sink: Sink) {
        val chunk = ByteArray(8192)
        runCatching {
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                if (n > 0) sink.add(chunk, n)
            }
        }
    }

    private companion object {
        const val MARK = "__mika_root_ok__"
        /** 只用于把命令内容挡在命令替换的尾部换行剥离之前;末尾精确移除一次,原命令里的同名文本不受影响。 */
        const val SENTINEL = "__mika_cmd_end__"
        const val MAX_BYTES = 1 shl 20
        const val GRACE_MS = 5_000L
        /** 跨任务串行:同一时间只跑一条 root 命令。 */
        val SERIAL = Mutex()
    }
}
