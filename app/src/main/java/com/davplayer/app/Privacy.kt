package com.davplayer.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToLong

/** 隐私模式暗码：输入它并按 = 才进入应用 */
const val PRIVACY_CODE = "19981212"

/**
 * 全局锁状态。Android 上 App() 负责维护，
 * 播放器 / 阅读器在 onResume 里检查，避免后台返回时绕过锁屏。
 */
object PrivacyGate {
    @Volatile
    var locked: Boolean = false
}

private enum class KeyKind { Digit, Func, Operator }

/**
 * 伪装成计算器的锁屏：支持四则运算、百分号、正负号、退格，
 * 输入 [PRIVACY_CODE] 后按 = 才回调 [onUnlock]。
 */
@Composable
fun CalculatorScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current

    var display by remember { mutableStateOf("0") }
    var expr by remember { mutableStateOf("") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(true) }

    fun reset() {
        display = "0"; expr = ""; acc = null; op = null; fresh = true
    }

    fun inputDigit(d: String) {
        if (fresh) {
            display = if (d == "0") "0" else d
            fresh = false
        } else if (display == "0") {
            if (d != "0") display = d
        } else if (display.replace("-", "").replace(".", "").length < 15) {
            display += d
        }
    }

    fun inputDot() {
        if (fresh) {
            display = "0."
            fresh = false
        } else if (!display.contains('.')) {
            display += "."
        }
    }

    fun toggleSign() {
        if (display == "0") return
        display = if (display.startsWith("-")) display.drop(1) else "-$display"
    }

    fun percent() {
        display = fmt((display.toDoubleOrNull() ?: 0.0) / 100.0)
        fresh = true
    }

    fun backspace() {
        if (fresh) return
        display = display.dropLast(1).ifEmpty { "0" }
        if (display == "0" || display == "-") {
            display = "0"
            fresh = true
        }
    }

    fun chooseOp(o: String) {
        val cur = display.toDoubleOrNull() ?: 0.0
        val a = acc
        if (a != null && op != null && !fresh) {
            val r = compute(a, cur, op!!)
            acc = r
            display = fmt(r)
        } else if (a == null) {
            acc = cur
        }
        op = o
        expr = "${fmt(acc ?: cur)} $o"
        fresh = true
    }

    fun doEquals() {
        // 暗码校验：必须是「当前输入就是这个数字」再按 =
        if (display == PRIVACY_CODE) {
            onUnlock()
            return
        }
        val b = display.toDoubleOrNull() ?: 0.0
        val a = acc
        val o = op
        if (a != null && o != null) {
            val r = compute(a, b, o)
            expr = "${fmt(a)} $o ${fmt(b)} ="
            display = fmt(r)
        } else {
            expr = "${fmt(b)} ="
        }
        acc = null
        op = null
        fresh = true
    }

    // 计算器页面不响应返回键，直接退出应用
    BackHandler { (ctx as? Activity)?.finish() }

    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxHeight()
                .padding(horizontal = 18.dp, vertical = 24.dp)
        ) {
            Spacer(Modifier.weight(1f))

            Text(
                expr,
                color = Color(0xFF8A8A8A),
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                display,
                color = Color.White,
                fontSize = when {
                    display.length <= 9 -> 64.sp
                    display.length <= 12 -> 48.sp
                    else -> 34.sp
                },
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )

            Spacer(Modifier.height(18.dp))

            val rows = listOf(
                listOf("C" to KeyKind.Func, "⌫" to KeyKind.Func, "%" to KeyKind.Func, "÷" to KeyKind.Operator),
                listOf("7" to KeyKind.Digit, "8" to KeyKind.Digit, "9" to KeyKind.Digit, "×" to KeyKind.Operator),
                listOf("4" to KeyKind.Digit, "5" to KeyKind.Digit, "6" to KeyKind.Digit, "−" to KeyKind.Operator),
                listOf("1" to KeyKind.Digit, "2" to KeyKind.Digit, "3" to KeyKind.Digit, "+" to KeyKind.Operator),
                listOf("±" to KeyKind.Digit, "0" to KeyKind.Digit, "." to KeyKind.Digit, "=" to KeyKind.Operator)
            )

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { (label, kind) ->
                            CalcKey(label, kind, Modifier.weight(1f)) {
                                when (label) {
                                    "C" -> reset()
                                    "⌫" -> backspace()
                                    "%" -> percent()
                                    "±" -> toggleSign()
                                    "." -> inputDot()
                                    "+", "−", "×", "÷" -> chooseOp(label)
                                    "=" -> doEquals()
                                    else -> inputDigit(label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    kind: KeyKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = when (kind) {
        KeyKind.Digit -> Color(0xFF333333)
        KeyKind.Func -> Color(0xFFA5A5A5)
        KeyKind.Operator -> Color(0xFFFF9F0A)
    }
    val fg = if (kind == KeyKind.Func) Color.Black else Color.White

    Box(
        modifier
            .height(66.dp)
            .background(bg, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 26.sp, fontWeight = FontWeight.Medium)
    }
}

private fun compute(a: Double, b: Double, op: String): Double = when (op) {
    "+" -> a + b
    "−" -> a - b
    "×" -> a * b
    "÷" -> if (b == 0.0) Double.NaN else a / b
    else -> b
}

private fun fmt(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "错误"
    val rounded = (v * 1e10).roundToLong() / 1e10
    return if (abs(rounded) < 1e15 && rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}
