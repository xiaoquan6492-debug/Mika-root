package com.wangzan101.mika.cap.phone.setup
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import com.wangzan101.mika.core.shell.HelperHandoff
import com.wangzan101.mika.core.shell.RootGrant
import com.wangzan101.mika.core.shell.RootStart
import com.wangzan101.mika.core.shell.SuAnswer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置页要的最小存法;测试用内存实现,App 用 SharedPreferences。 */
internal interface RootStore {
    fun getBoolean(key: String, def: Boolean): Boolean
    fun getString(key: String): String?
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String?)
}

private class PrefsStore(private val prefs: SharedPreferences) : RootStore {
    override fun getBoolean(key: String, def: Boolean) = prefs.getBoolean(key, def)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    override fun putString(key: String, value: String?) { prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
}

/**
 * 已 root 的手机上的两个开关,都默认关、只在找得到 su 时显示:
 * 「用 root 启动手机助手」(看门狗经 su 以 shell 身份起助手,见 [com.wangzan101.mika.core.shell.BuiltinShellEngine])、
 * 「允许 Mika 执行 root 命令」(Agent 的 root 工具)。打开时问 su 一次;su 的答复记下来,拒绝了就不再自己去问,
 * 等用户点重试。问 su 都在 IO 线程上,不堵界面。[changed] 在「用 root 启动」能不能用变了时调(让看门狗按新的情况再试)。
 *
 * 两个开关互不授权;命令开关是否可用在每次 root 执行前由 [commandsAllowed] 实时判断,不能只看任务创建时的快照。
 *
 * 请求代际、持久化值和 StateFlow 的提交都在 [lock] 内完成:允许结果不会在关闭之后又把开关写成打开。
 * 外部 [probe] 和 [changed] 不持锁调用;外部 su 查找也不在锁内做。
 */
class RootControl internal constructor(
    private val store: RootStore,
    private val scope: CoroutineScope,
    private val changed: () -> Unit,
    private val probe: suspend (Int) -> SuAnswer?,
    private val io: CoroutineDispatcher,
    private val findSu: () -> Boolean = { RootShell.su() != null },
) {
    constructor(context: Context, scope: CoroutineScope, changed: () -> Unit) : this(
        PrefsStore(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)),
        scope, changed, { uid -> RootShell.ask(uid, ASK_TIMEOUT_MS) }, Dispatchers.IO,
    )

    /** 串行化代际检查 + prefs + StateFlow;等待 su 和回调不放在锁内。 */
    private val lock = Any()
    private var startRequest = 0L
    private var commandRequest = 0L

    /**
     * 设置页看的样子。[suPresent]:找得到 su(找不到就整个不显示);[start]、[commands]:两个开关;
     * [startGrant]、[commandsGrant]:su 对这两件事的答复(问的时候是 [RootGrant.Pending])。
     */
    data class State(
        val suPresent: Boolean = false,
        val start: Boolean = false,
        val startGrant: RootGrant = RootGrant.Unknown,
        val commands: Boolean = false,
        val commandsGrant: RootGrant = RootGrant.Unknown,
    )

    private val _state = MutableStateFlow(
        State(
            suPresent = false,
            start = store.getBoolean(KEY_START, false),
            startGrant = RootGrant.of(answer(KEY_START_ANSWER)),
            commands = store.getBoolean(KEY_COMMANDS, false),
            commandsGrant = RootGrant.of(answer(KEY_COMMANDS_ANSWER)),
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    /** su 在不在:第一次用时当场看一眼(只是几个文件在不在),之后由 [refresh] 更新。 */
    @Volatile private var suFound: Boolean? = null

    private fun suPresent(): Boolean {
        suFound?.let { return it }
        // 外部文件检查不持锁;两个并发第一次检查都以同一个结果为准。
        val found = findSu()
        synchronized(lock) {
            if (suFound == null) {
                suFound = found
                _state.update { it.copy(suPresent = found) }
            }
            return suFound == true
        }
    }

    /** 重新找一次 su(App 启动、打开设置页时);变了就让看门狗按新的情况再试。 */
    fun refresh() {
        scope.launch(io) {
            val before = suFound
            val found = findSu()
            val notify = synchronized(lock) {
                val changedSu = before != null && before != found
                suFound = found
                _state.update { it.copy(suPresent = found) }
                changedSu && _state.value.start
            }
            if (notify) changed()
        }
    }

    /** 看门狗看的:「用 root 启动」此刻能不能用。 */
    fun rootStart(): RootStart {
        val present = suPresent()
        return synchronized(lock) {
            RootStart.of(_state.value.start, present, answer(KEY_START_ANSWER))
        }
    }

    /** 看门狗起助手时 su 的答复;null 是忘掉上一次的拒绝(用户点了重试)。外部答案也让待处理的旧询问失效。 */
    fun setStartAnswer(a: SuAnswer?) {
        synchronized(lock) {
            startRequest++
            applyStartAnswerLocked(a)
        }
    }

    private fun applyStartAnswerLocked(a: SuAnswer?) {
        store.putString(KEY_START_ANSWER, a?.name)
        _state.update { it.copy(startGrant = RootGrant.of(a)) }
    }

    /** 经 su 起助手时发现 su 没了;待处理的 su 询问结果都不再算数。 */
    fun suGone() {
        synchronized(lock) { suGoneLocked() }
    }

    private fun suGoneLocked() {
        // 不管之前是否已经发现 su 不在,都让所有在途请求失效
        startRequest++
        commandRequest++
        if (suFound == false) return
        suFound = false
        _state.update { it.copy(suPresent = false) }
    }

    /**
     * 打开或关掉「用 root 启动」。打开时以 shell 身份问 su 一次(第一次会弹授权框):答应了看门狗马上经 su 起助手;
     * 拒绝、不回话或切不到 shell 身份都照实记下,开关留着,设置里说明并给重试。
     */
    fun setStart(on: Boolean) {
        if (!on) {
            synchronized(lock) {
                startRequest++
                store.putBoolean(KEY_START, false)
                _state.update {
                    it.copy(start = false, startGrant = if (it.startGrant == RootGrant.Pending) RootGrant.Unknown else it.startGrant)
                }
            }
            changed()
            return
        }
        val token = synchronized(lock) {
            store.putBoolean(KEY_START, true)
            startRequest++
            _state.update { it.copy(start = true, startGrant = RootGrant.Pending) }
            startRequest
        }
        askForStart(token)
    }

    /** 重新问 su(用户点了重试)。 */
    fun retryStart() {
        val token = synchronized(lock) {
            if (!_state.value.start) return
            startRequest++
            _state.update { it.copy(startGrant = RootGrant.Pending) }
            startRequest
        }
        askForStart(token)
    }

    private fun askForStart(token: Long) {
        scope.launch {
            val a = withContext(io) { probe(HelperHandoff.SHELL_UID) }
            val notify = synchronized(lock) {
                if (token != startRequest || !_state.value.start) return@synchronized false
                if (a == null) suGoneLocked() else applyStartAnswerLocked(a)
                true
            }
            if (notify) changed()
        }
    }

    /** 解除配对/停止手机操作:两个 root 开关都关掉,避免解除后仍可执行 root 命令。 */
    fun turnOffAll() {
        synchronized(lock) {
            startRequest++
            commandRequest++
            store.putBoolean(KEY_START, false)
            store.putBoolean(KEY_COMMANDS, false)
            store.putString(KEY_START_ANSWER, null)
            store.putString(KEY_COMMANDS_ANSWER, null)
            _state.update { it.copy(start = false, startGrant = RootGrant.Unknown, commands = false, commandsGrant = RootGrant.Unknown) }
        }
        changed()
    }

    /** Agent 能用 root 工具:用户打开了、su 答应过、su 还在。每次执行前都会重新判断。 */
    fun commandsAllowed(): Boolean {
        if (!suPresent()) return false
        return synchronized(lock) { _state.value.commands && answer(KEY_COMMANDS_ANSWER) == SuAnswer.Granted }
    }

    /** 打开或关掉「允许 Mika 执行 root 命令」。打开时以 root 问 su 一次,答应了才打开。 */
    fun setCommands(on: Boolean) {
        if (!on) {
            synchronized(lock) {
                commandRequest++
                store.putBoolean(KEY_COMMANDS, false)
                _state.update { it.copy(commands = false, commandsGrant = if (it.commandsGrant == RootGrant.Pending) RootGrant.Unknown else it.commandsGrant) }
            }
            return
        }
        val token = synchronized(lock) {
            commandRequest++
            // 询问期间先保持关闭并持久化关闭:旧授权不能在这段时间继续放行
            store.putBoolean(KEY_COMMANDS, false)
            _state.update { it.copy(commands = false, commandsGrant = RootGrant.Pending) }
            commandRequest
        }
        scope.launch {
            val a = withContext(io) { probe(0) }
            synchronized(lock) {
                if (token != commandRequest) return@synchronized
                if (a == null) {
                    store.putBoolean(KEY_COMMANDS, false)
                    store.putString(KEY_COMMANDS_ANSWER, null)
                    _state.update { it.copy(commands = false, commandsGrant = RootGrant.Unknown) }
                    suGoneLocked()
                } else {
                    store.putBoolean(KEY_COMMANDS, a == SuAnswer.Granted)
                    store.putString(KEY_COMMANDS_ANSWER, a.name)
                    _state.update { it.copy(commands = a == SuAnswer.Granted, commandsGrant = RootGrant.of(a)) }
                }
            }
        }
    }

    /** 执行 root 命令时 su 拒绝了:关掉这一项并记下(下次打开时再问),同时让待处理的开请求失效。 */
    fun commandsDenied(a: SuAnswer) {
        synchronized(lock) {
            commandRequest++
            store.putBoolean(KEY_COMMANDS, false)
            store.putString(KEY_COMMANDS_ANSWER, a.name)
            _state.update { it.copy(commands = false, commandsGrant = RootGrant.of(a)) }
        }
    }

    private fun answer(key: String): SuAnswer? = store.getString(key)?.let { n -> SuAnswer.entries.firstOrNull { it.name == n } }

    private companion object {
        const val PREFS = "root"
        const val KEY_START = "start"
        const val KEY_START_ANSWER = "startAnswer"
        const val KEY_COMMANDS = "commands"
        const val KEY_COMMANDS_ANSWER = "commandsAnswer"
        /** 用户打开开关时问 su:授权框要给人看清、点一下的时间。 */
        const val ASK_TIMEOUT_MS = 60_000L
    }
}
