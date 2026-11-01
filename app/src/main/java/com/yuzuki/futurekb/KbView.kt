package com.yuzuki.futurekb

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

// code: Set1スキャンコード。0xE0xx は拡張キー
data class Key(
    val label: String, val sub: String, val code: Int,
    val x: Float, val y: Float, val w: Float, val h: Float
)

private const val COLS = 18.5f
private const val ROWS = 6f

private fun buildKeys(): List<Key> {
    val ks = mutableListOf<Key>()
    fun add(l: String, s: String, c: Int, x: Float, y: Float, w: Float = 1f, h: Float = 1f) {
        ks += Key(l, s, c, x, y, w, h)
    }
    // Row0
    add("Esc", "", 0x01, 0f, 0f, 1f, 0.8f)
    for (i in 0 until 10) add("F${i + 1}", "", 0x3B + i, 1.5f + i, 0f, 1f, 0.8f)
    add("F11", "", 0x57, 11.5f, 0f, 1f, 0.8f)
    add("F12", "", 0x58, 12.5f, 0f, 1f, 0.8f)
    // Row1
    add("半/全", "", 0x29, 0f, 1f)
    val dsub = listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")", "")
    for (i in 0 until 10) add(if (i == 9) "0" else "${i + 1}", dsub[i], 0x02 + i, 1f + i, 1f)
    add("-", "=", 0x0C, 11f, 1f)
    add("^", "~", 0x0D, 12f, 1f)
    add("¥", "|", 0x7D, 13f, 1f)
    add("⌫", "", 0x0E, 14f, 1f)
    // Row2
    add("Tab", "", 0x0F, 0f, 2f, 1.5f)
    "qwertyuiop".forEachIndexed { i, c -> add(c.uppercase(), "", 0x10 + i, 1.5f + i, 2f) }
    add("@", "`", 0x1A, 11.5f, 2f)
    add("[", "{", 0x1B, 12.5f, 2f)
    add("Enter", "", 0x1C, 13.5f, 2f, 1.5f, 2f)
    // Row3
    add("英数", "", 0x3A, 0f, 3f, 1.5f)
    "asdfghjkl".forEachIndexed { i, c -> add(c.uppercase(), "", 0x1E + i, 1.5f + i, 3f) }
    add(";", "+", 0x27, 10.5f, 3f)
    add(":", "*", 0x28, 11.5f, 3f)
    add("]", "}", 0x2B, 12.5f, 3f)
    // Row4
    add("Shift", "", 0x2A, 0f, 4f, 2.25f)
    "zxcvbnm".forEachIndexed { i, c -> add(c.uppercase(), "", 0x2C + i, 2.25f + i, 4f) }
    add(",", "<", 0x33, 9.25f, 4f)
    add(".", ">", 0x34, 10.25f, 4f)
    add("/", "?", 0x35, 11.25f, 4f)
    add("\\", "_", 0x73, 12.25f, 4f)
    add("Shift", "", 0x36, 13.25f, 4f, 1.75f)
    // Row5
    add("Ctrl", "", 0x1D, 0f, 5f, 1.25f)
    add("Win", "", 0xE05B, 1.25f, 5f, 1.25f)
    add("Alt", "", 0x38, 2.5f, 5f, 1.25f)
    add("無変換", "", 0x7B, 3.75f, 5f, 1.25f)
    add("", "", 0x39, 5f, 5f, 4.5f)
    add("変換", "", 0x79, 9.5f, 5f, 1.25f)
    add("かな", "", 0x70, 10.75f, 5f, 1.25f)
    add("Alt", "", 0xE038, 12f, 5f, 1.25f)
    add("Ctrl", "", 0xE01D, 13.25f, 5f, 1.75f)
    // Right cluster
    val rx = 15.5f
    add("Ins", "", 0xE052, rx, 1f)
    add("Home", "", 0xE047, rx + 1, 1f)
    add("PgUp", "", 0xE049, rx + 2, 1f)
    add("Del", "", 0xE053, rx, 2f)
    add("End", "", 0xE04F, rx + 1, 2f)
    add("PgDn", "", 0xE051, rx + 2, 2f)
    add("↑", "", 0xE048, rx + 1, 4f)
    add("←", "", 0xE04B, rx, 5f)
    add("↓", "", 0xE050, rx + 1, 5f)
    add("→", "", 0xE04D, rx + 2, 5f)
    return ks
}

class KbView(ctx: Context) : View(ctx) {
    private val keys = buildKeys()
    private val held = HashMap<Int, Key>()

    private val bg = Paint()
    private val grid = Paint().apply { color = Color.argb(22, 0, 229, 255); strokeWidth = 1f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }
    private val subTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textAlign = Paint.Align.LEFT
    }

    private var phase = 0f
    private var ux = 1f
    private var uy = 1f
    private var ox = 0f
    private var oy = 0f

    init {
        ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = 7000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = min(w, h) * 0.02f
        ox = pad; oy = pad
        ux = (w - 2 * pad) / COLS
        uy = (h - 2 * pad) / ROWS
        bg.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.parseColor("#04050E"), Color.parseColor("#0A1230"), Shader.TileMode.CLAMP
        )
    }

    private fun rect(k: Key, g: Float) = RectF(
        ox + k.x * ux + g, oy + k.y * uy + g,
        ox + (k.x + k.w) * ux - g, oy + (k.y + k.h) * uy - g
    )

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawRect(0f, 0f, w, h, bg)
        var gx = 0f
        while (gx < w) { c.drawLine(gx, 0f, gx, h, grid); gx += 48f }
        var gy = 0f
        while (gy < h) { c.drawLine(0f, gy, w, gy, grid); gy += 48f }

        val hue = 188f + 28f * sin(phase)
        val neon = Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))
        val nr = Color.red(neon); val ng = Color.green(neon); val nb = Color.blue(neon)
        val m = min(ux, uy)
        val g = m * 0.06f
        val rad = m * 0.14f

        for (k in keys) {
            val r = rect(k, g)
            val pressed = held.values.any { it === k }
            fill.color = if (pressed) Color.argb(120, nr, ng, nb) else Color.argb(235, 10, 16, 40)
            c.drawRoundRect(r, rad, rad, fill)
            stroke.color = if (pressed) neon else Color.argb(180, nr, ng, nb)
            stroke.strokeWidth = if (pressed) 3.2f else 1.6f
            stroke.setShadowLayer(if (pressed) 24f else 10f, 0f, 0f, neon)
            c.drawRoundRect(r, rad, rad, stroke)

            if (k.label.isNotEmpty()) {
                txt.color = Color.parseColor("#E8F8FF")
                txt.textSize = if (k.label.length > 1) m * 0.24f else m * 0.4f
                val ty = r.centerY() - (txt.descent() + txt.ascent()) / 2
                c.drawText(k.label, r.centerX(), ty, txt)
            }
            if (k.sub.isNotEmpty()) {
                subTxt.color = Color.argb(170, nr, ng, nb)
                subTxt.textSize = m * 0.24f
                c.drawText(k.sub, r.left + m * 0.1f, r.top + m * 0.26f, subTxt)
            }
        }

        // ステータス
        val on = Net.connected
        txt.color = if (on) Color.parseColor("#39FF88") else Color.parseColor("#FF4D6A")
        txt.textSize = m * 0.26f
        txt.textAlign = Paint.Align.RIGHT
        c.drawText(
            if (on) "● LINK ONLINE" else "○ NO LINK",
            ox + COLS * ux - m * 0.1f, oy + 0.55f * uy, txt
        )
        txt.textAlign = Paint.Align.CENTER
    }

    private fun hit(x: Float, y: Float): Key? =
        keys.firstOrNull { rect(it, 0f).contains(x, y) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val k = hit(e.getX(i), e.getY(i))
                if (k != null) {
                    held[e.getPointerId(i)] = k
                    Net.send("D %X".format(k.code))
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                held.remove(e.getPointerId(e.actionIndex))?.let {
                    Net.send("U %X".format(it.code))
                }
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        return true
    }

    fun releaseAll() {
        held.values.forEach { Net.send("U %X".format(it.code)) }
        held.clear()
    }
}
