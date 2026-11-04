package com.davplayer.app.ios.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class LogEntry(
    val time: Long,
    val level: String,
    val tag: String,
    val message: String
)

/**
 * 内存运行日志：网络请求、接口错误、播放器/阅读器关键事件。
 * 上限 300 条，退出应用后清空（后续可落盘）。
 */
object AppLog {
    private const val MAX = 300
    private val scope = CoroutineScope(Dispatchers.Main)

    var entries: List<LogEntry> by mutableStateOf(emptyList())
        private set

    fun info(tag: String, message: String) = add("I", tag, message)
    fun warn(tag: String, message: String) = add("W", tag, message)
    fun error(tag: String, message: String) = add("E", tag, message)


    private fun add(level: String, tag: String, message: String) {
        val entry = LogEntry(nowMillis(), level, tag, message)
        scope.launch {
            entries = (entries + entry).takeLast(MAX)
        }
    }

    fun clear() {
        scope.launch { entries = emptyList() }
    }
}
