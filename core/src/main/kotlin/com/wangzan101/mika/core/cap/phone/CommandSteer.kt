package com.wangzan101.mika.core.cap.phone
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

/**
 * 系统命令在做专门工具做的事(点、滑、打字、按键、打开 App)时,结果里提醒换回那个工具:专门工具在 Mika 的屏上做,
 * 做完会核实效果,shell 发的输入不核实。第一次只提醒、照样执行;本任务提醒过之后,再做这几类事的命令不执行,
 * 只回那句提醒([refuses])。用 shell 分享文字一律不执行,指向分享文字的工具。查状态、读设置一类的命令不受影响。
 */
internal object CommandSteer {
    enum class Purpose(val feedbackKey: String) {
        Gesture("system_command.use_tap"), Type("system_command.use_type"), Key("system_command.use_key"), Launch("system_command.use_launch"),
        Share("system_command.use_share"),
        /** 常规 shell 里明显在调 su:统一引导到专用 root_run_command,不在这里执行。 */
        Root("system_command.use_root"),
    }

    /** [purpose] 的命令这回不执行:分享、明显 su 一律不执行;其余在本任务已提醒过([steered])之后不执行。 */
    fun refuses(purpose: Purpose?, steered: Boolean): Boolean =
        purpose == Purpose.Share || purpose == Purpose.Root || (purpose != null && steered)

    /** 命令里每一段 `input …` 的子命令(前面可带输入源和 -d 屏号)。 */
    private val INPUT = Regex("""(?:^|[;&|(\s])input\s+(?:(?:touchscreen|touchpad|keyboard|mouse|stylus|trackball|dpad|gamepad|joystick|rotaryencoder|touchnavigation)\s+)?(?:-d\s+\S+\s+)?([a-z]+)""")
    private val LAUNCH = Regex("""(?:^|[;&|(\s])(?:am\s+(?:start|start-activity)\b|cmd\s+activity\s+start(?:-activity)?\b|monkey\s+(?:.*\s)?-p\s)""")
    private val GESTURES = setOf("tap", "swipe", "draganddrop", "motionevent", "press", "roll")
    /** 启动的是分享:动作写全(android.intent.action.SEND)或只写 -a SEND。 */
    private val SHARE = Regex("""(?:android\.intent\.action\.SEND|-a\s+["']?SEND)(?![A-Za-z_])""")
    /** 分享的是文件(带 STREAM):分享文字的工具做不了,按一般的启动算。 */
    private const val STREAM = "android.intent.extra.STREAM"

    /** 变量赋值前缀(命令位置上的赋值不算执行)。 */
    private val ASSIGNMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*")

    /**
     * 这条命令是否在明显调用 su。按有限的命令位置扫描:只把命令名、env/toybox/busybox 后面的参数、
     * shell -c 的脚本文本当作命令;引号里的普通参数和注释不误判。这不是完整 shell 解析,编码/变量拼接仍可绕过。
     */
    fun suRequest(command: String): Boolean = scan(command)

    private fun scan(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '#' -> {
                    // 注释只在命令/空白之后有意义;单词里的 # 已由 readWord 收进参数
                    while (i < s.length && s[i] != '\n') i++
                }
                c == ';' || c == '|' || c == '&' || c == '(' || c == ')' -> i++
                c == '$' && i + 1 < s.length && s[i + 1] == '(' -> i += 2
                else -> {
                    val words = mutableListOf<String>()
                    var j = i
                    while (j < s.length) {
                        // 未引用的 LF 是命令结束,不能把下一条命令的字接到本段参数里;
                        // 引号里的 LF 已由 readWord 收进同一个词,不受这里影响。
                        while (j < s.length && s[j].isWhitespace() && s[j] != '\n' && s[j] != '\r') j++
                        if (j >= s.length) break
                        if (s[j] == '\n' || s[j] == '\r') break
                        val d = s[j]
                        if (d == ';' || d == '|' || d == '&' || d == '(' || d == ')' || d == '#') break
                        if (d == '$' && j + 1 < s.length && s[j + 1] == '(') break
                        val (w, next) = readWord(s, j)
                        if (w.isEmpty()) { j++; continue }
                        words += w
                        j = next
                    }
                    if (executesSu(words)) return true
                    i = j
                }
            }
        }
        return false
    }

    /** 读一个 shell 词:保留引号里的内容,处理反斜杠,遇到空白/控制符停下。 */
    private fun readWord(s: String, start: Int): Pair<String, Int> {
        val b = StringBuilder()
        var i = start
        var quote: Char? = null
        while (i < s.length) {
            val c = s[i]
            if (quote != null) {
                when {
                    c == quote -> { quote = null; i++ }
                    c == '\\' && quote == '"' && i + 1 < s.length -> { b.append(s[i + 1]); i += 2 }
                    else -> { b.append(c); i++ }
                }
            } else {
                when {
                    c == '\'' || c == '"' -> { quote = c; i++ }
                    c == '\\' && i + 1 < s.length -> { b.append(s[i + 1]); i += 2 }
                    c.isWhitespace() || c == ';' || c == '|' || c == '&' || c == '(' || c == ')' || c == '<' || c == '>' -> break
                    c == '$' && i + 1 < s.length && s[i + 1] == '(' -> break
                    else -> { b.append(c); i++ }
                }
            }
        }
        return b.toString() to i
    }

    /** 命令行开头的词是不是在调 su;包装词只认命令位置,不认 echo 的参数。 */
    private fun executesSu(words: List<String>): Boolean {
        var i = 0
        while (i < words.size) {
            val w = words[i]
            if (ASSIGNMENT.matches(w) || w in setOf("nohup", "setsid", "exec", "command")) { i++; continue }
            if (w == "env") {
                i++
                while (i < words.size && words[i].startsWith("-")) {
                    val option = words[i++]
                    if (option == "--") break
                    if (option == "-u" || option == "--unset" || option in setOf("-C", "--chdir", "-S", "--split-string")) i++
                }
                continue
            }
            if (w == "toybox" || w == "busybox") {
                i++
                return i < words.size && isSu(words[i])
            }
            if (w in setOf("sh", "bash", "mksh", "ash", "dash", "ksh")) {
                val c = words.indexOfFirst { it.startsWith("-") && 'c' in it.drop(1) }
                return c >= 0 && c + 1 < words.size && scan(words[c + 1])
            }
            return isSu(w)
        }
        return false
    }

    private fun isSu(word: String) = word == "su" || word.endsWith("/su")

    /**
     * 命令在模仿哪一类专门工具;都不是为 null。打字和点同在一条命令里时按打字算(要说的是打字该用什么);
     * 分享文字排在最前:它一律不执行。
     */
    fun purpose(command: String): Purpose? {
        if (suRequest(command)) return Purpose.Root
        val subs = INPUT.findAll(command).map { it.groupValues[1] }.toSet()
        val launches = LAUNCH.containsMatchIn(command)
        return when {
            launches && SHARE.containsMatchIn(command) && STREAM !in command -> Purpose.Share
            "text" in subs -> Purpose.Type
            subs.any { it in GESTURES } -> Purpose.Gesture
            subs.any { it == "keyevent" || it == "keycombination" } -> Purpose.Key
            launches -> Purpose.Launch
            else -> null
        }
    }
}
