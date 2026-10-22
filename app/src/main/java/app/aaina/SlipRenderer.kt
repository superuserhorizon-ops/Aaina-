package app.aaina

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SlipBuilder {
    fun fold(list: List<AppTime>, hidden: Set<String>, all: Boolean): List<AppTime> {
        val vis = list.filter { !(all || it.name in hidden) }
        val hid = list.filter { all || it.name in hidden }.sumOf { it.ms }
        return if (hid > 0) vis + AppTime("Private apps", hid) else vis
    }

    /** w = 7 days, index 6 is today. */
    fun build(ctx: Context, w: List<List<AppTime>>, sel: Int, theme: String, color: Int, limitMin: Int,
              hidden: Set<String>, hideAll: Boolean, compact: Boolean): Bitmap {
        val limitMs = limitMin * 60000L
        val totals = w.map { d -> d.sumOf { it.ms } }
        var streak = 0
        var k = sel
        while (k >= 0 && totals[k] <= limitMs) { streak++; k-- }
        val badges = mutableListOf<String>()
        if (streak >= 5) badges += "5-day streak" else if (streak >= 3) badges += "3-day streak"
        if (totals[sel] > 0 && totals[sel] == totals.min()) badges += "Best day"
        if (totals[sel] in 1L..7_200_000L) badges += "Under 2h"
        val day = LocalDate.now().minusDays((6 - sel).toLong())
        return SlipRenderer.render(
            ctx, day.format(DateTimeFormatter.ofPattern("d MMM yyyy")), 1000 + day.dayOfYear,
            fold(w[sel], hidden, hideAll), if (sel > 0) fold(w[sel - 1], hidden, hideAll) else null,
            limitMs, streak, badges, theme, color, compact, !Prefs(ctx).pro
        )
    }
}

/** Draws the receipt as a bitmap in memory. Nothing is saved. */
object SlipRenderer {
    private const val W = 600
    private const val M = 18f
    private const val PAD = 30f
    private const val LH = 40f

    fun fmt(ms: Long): String { val m = ms / 60000; return "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m" }

    private class Ln(val k: Int, val l: String = "", val r: String = "", val bold: Boolean = false,
                     val col: Int? = null, val sz: Float = 25f, val h: Float = LH)

    fun render(ctx: Context, date: String, no: Int, apps: List<AppTime>, prev: List<AppTime>?, limitMs: Long,
               streak: Int, badges: List<String>, theme: String, accent: Int, compact: Boolean, footer: Boolean): Bitmap {
        val dark = theme == "dark"
        val colorful = theme == "color"
        val paper = if (dark) Color.parseColor("#1A2226") else if (colorful) ColorUtils.blendARGB(accent, Color.WHITE, 0.84f) else Color.parseColor("#FBFBF7")
        val ink = Color.parseColor(if (dark) "#E6EFE9" else if (colorful) "#2B1D4A" else "#1B1B1B")
        val good = Color.parseColor(if (dark) "#6FE0C8" else "#1F7A6D")
        val bad = Color.parseColor(if (dark) "#FF9A80" else "#B4472F")
        val head = if (colorful) ColorUtils.blendARGB(accent, Color.BLACK, 0.35f) else ink
        val mat = Color.parseColor(if (dark) "#0F1A1E" else "#E4EBE9")
        val barCol = if (colorful) accent else ink

        val prevMap = prev?.associate { it.name to it.ms }
        val total = apps.sumOf { it.ms }
        val maxApps = if (compact) 4 else 8
        val shown = apps.take(maxApps)
        val rest = apps.drop(maxApps)

        val rows = ArrayList<Ln>()
        rows += Ln(0, "DAILY APP SLIP", bold = true, col = head, sz = 30f, h = 46f)
        rows += Ln(0, date)
        if (!compact) rows += Ln(0, "Slip no. $no")
        rows += Ln(2)
        shown.forEach { a ->
            val p = prevMap?.get(a.name)
            val arrow = if (p == null) "" else if (a.ms > p) " ▲" else if (a.ms < p) " ▼" else ""
            rows += Ln(1, a.name.take(16), fmt(a.ms) + arrow)
        }
        if (rest.isNotEmpty()) rows += Ln(1, "Others (${rest.size})", fmt(rest.sumOf { it.ms }))
        if (apps.isEmpty()) rows += Ln(0, "No usage yet")
        rows += Ln(2)
        rows += Ln(1, "TOTAL", fmt(total), bold = true)
        rows += Ln(1, "Limit", fmt(limitMs))
        val diff = limitMs - total
        rows += Ln(1, if (diff >= 0) "Saved" else "Over by", fmt(kotlin.math.abs(diff)), bold = true, col = if (diff >= 0) good else bad)
        if (prev != null) {
            val d = total - prev.sumOf { it.ms }
            rows += Ln(1, "vs previous day", (if (d > 0) "▲ " else "▼ ") + fmt(kotlin.math.abs(d)), col = if (d > 0) bad else good)
        }
        if (!compact) {
            rows += Ln(2)
            rows += Ln(0, "Streak under limit: $streak day${if (streak == 1) "" else "s"}")
            badges.forEach { rows += Ln(0, "★ $it ★", bold = true, col = head) }
            rows += Ln(2)
            rows += Ln(0, "Items purchased: ${apps.size} apps")
            rows += Ln(0, "Printed on your phone. Not saved.", sz = 21f)
            rows += Ln(3, h = 64f)
            rows += Ln(0, "*** THANK YOU ***", bold = true)
            if (footer) rows += Ln(0, "Made with Aaina", sz = 21f)
        } else if (footer) {
            rows += Ln(0, "Made with Aaina", sz = 21f)
        }

        val zig = 22f
        val h = (2 * M + 2 * PAD + rows.sumOf { it.h.toDouble() } + zig).toInt()
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(mat)

        val top = M
        val bodyBottom = h - M - zig
        val tooth = (W - 2 * M) / 28f
        val path = Path()
        path.moveTo(M, top); path.lineTo(W - M, top); path.lineTo(W - M, bodyBottom)
        var x = W - M
        while (x - tooth > M - 0.5f) {
            path.lineTo(x - tooth / 2, bodyBottom + zig)
            path.lineTo(x - tooth, bodyBottom)
            x -= tooth
        }
        path.lineTo(M, bodyBottom); path.close()
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper })

        val reg = ResourcesCompat.getFont(ctx, R.font.courierprime_regular) ?: Typeface.MONOSPACE
        val bold = ResourcesCompat.getFont(ctx, R.font.courierprime_bold) ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint().apply { color = ink; strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f) }
        val left = M + PAD
        val right = W - M - PAD
        var y = M + PAD
        for (ln in rows) {
            p.typeface = if (ln.bold) bold else reg
            p.textSize = ln.sz
            p.color = ln.col ?: ink
            val base = y + 28f
            when (ln.k) {
                0 -> { p.textAlign = Paint.Align.CENTER; c.drawText(ln.l, W / 2f, base, p) }
                1 -> {
                    p.textAlign = Paint.Align.LEFT; c.drawText(ln.l, left, base, p)
                    p.textAlign = Paint.Align.RIGHT; c.drawText(ln.r, right, base, p)
                    p.textAlign = Paint.Align.LEFT
                    val dot = p.measureText(".")
                    val free = right - left - p.measureText(ln.l) - p.measureText(ln.r) - 2 * dot
                    if (free > dot) c.drawText(".".repeat((free / dot).toInt()), left + p.measureText(ln.l) + dot, base, p)
                }
                2 -> c.drawLine(left, y + 18f, right, y + 18f, stroke)
                else -> {
                    val rnd = java.util.Random(no.toLong())
                    val bp = Paint().apply { color = barCol }
                    var bx = left + 40f
                    var i = 0
                    while (bx < right - 40f) {
                        val bw = (1 + rnd.nextInt(3)) * 2f
                        if (i % 2 == 0) c.drawRect(bx, y + 6f, bx + bw, y + ln.h - 8f, bp)
                        bx += bw; i++
                    }
                }
            }
            y += ln.h
        }
        return bmp
    }
}
