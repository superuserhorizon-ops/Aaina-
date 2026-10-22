package app.aaina

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface

/** Draws the receipt as a bitmap in memory. */
object SlipRenderer {
    private const val W = 560
    private const val PAD = 28f
    private const val LH = 38f

    fun fmt(ms: Long): String { val m = ms / 60000; return "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m" }

    private class Line(val kind: Int, val l: String = "", val r: String = "", val bold: Boolean = false, val color: Int = Color.parseColor("#1B1B1B"))

    fun render(date: String, apps: List<AppTime>, prev: List<AppTime>?, limitMs: Long, streak: Int): Bitmap {
        val total = apps.sumOf { it.ms }
        val prevMap = prev?.associate { it.name to it.ms }
        val good = Color.parseColor("#1F7A6D"); val bad = Color.parseColor("#B4472F")
        val L = mutableListOf<Line>()
        L += Line(0, "DAILY APP SLIP", bold = true); L += Line(0, date); L += Line(2)
        apps.take(8).forEach { a ->
            val p = prevMap?.get(a.name)
            val arrow = if (p == null) "" else if (a.ms > p) " ▲" else if (a.ms < p) " ▼" else ""
            L += Line(1, a.name.take(16), fmt(a.ms) + arrow)
        }
        if (apps.size > 8) L += Line(1, "Others (${apps.size - 8})", fmt(apps.drop(8).sumOf { it.ms }))
        if (apps.isEmpty()) L += Line(0, "No usage yet")
        L += Line(2); L += Line(1, "TOTAL", fmt(total), bold = true); L += Line(1, "Limit", fmt(limitMs))
        val diff = limitMs - total
        L += Line(1, if (diff >= 0) "Saved" else "Over by", fmt(kotlin.math.abs(diff)), bold = true, color = if (diff >= 0) good else bad)
        prev?.let { pt -> val d = total - pt.sumOf { it.ms }; L += Line(1, "vs previous day", (if (d > 0) "▲ " else "▼ ") + fmt(kotlin.math.abs(d)), color = if (d > 0) bad else good) }
        L += Line(2); L += Line(0, "Streak under limit: $streak day${if (streak == 1) "" else "s"}")
        L += Line(0, "Items purchased: ${apps.size} apps"); L += Line(2)
        L += Line(0, "Printed on your phone. Not saved."); L += Line(0, "*** THANK YOU ***", bold = true); L += Line(0, "Made with Aaina")

        val h = (PAD * 2 + L.size * LH).toInt()
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.parseColor("#FBFBF7"))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f }
        val stroke = Paint().apply { color = Color.parseColor("#1B1B1B"); strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f) }
        var y = PAD + 26f
        for (ln in L) {
            p.typeface = if (ln.bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
            p.color = ln.color
            when (ln.kind) {
                0 -> { p.textAlign = Paint.Align.CENTER; c.drawText(ln.l, W / 2f, y, p) }
                1 -> {
                    p.textAlign = Paint.Align.LEFT; c.drawText(ln.l, PAD, y, p)
                    p.textAlign = Paint.Align.RIGHT; c.drawText(ln.r, W - PAD, y, p)
                    p.textAlign = Paint.Align.LEFT
                    val dot = p.measureText("."); val free = W - 2 * PAD - p.measureText(ln.l) - p.measureText(ln.r) - 2 * dot
                    if (free > dot) c.drawText(".".repeat((free / dot).toInt()), PAD + p.measureText(ln.l) + dot, y, p)
                }
                else -> c.drawLine(PAD, y - 10f, W - PAD, y - 10f, stroke)
            }
            y += LH
        }
        return bmp
    }
}
