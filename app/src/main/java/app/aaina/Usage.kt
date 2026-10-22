package app.aaina

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

data class AppTime(val name: String, val ms: Long)

/** Reads usage live from Android. Nothing is copied or stored. */
object Usage {
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun forDay(ctx: Context, daysAgo: Int): List<AppTime> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = ctx.packageManager
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now().minusDays(daysAgo.toLong())
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        val totals = HashMap<String, Long>()
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        var cur: String? = null
        var since = 0L
        fun close(at: Long) { cur?.let { totals[it] = (totals[it] ?: 0L) + (at - since) }; cur = null }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> if (cur != e.packageName) { close(e.timeStamp); cur = e.packageName; since = e.timeStamp }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (cur == e.packageName) close(e.timeStamp)
            }
        }
        close(end)
        return totals
            .filter { it.value >= 60_000 && it.key != ctx.packageName && pm.getLaunchIntentForPackage(it.key) != null }
            .map { (pkg, ms) ->
                val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (x: Exception) { pkg }
                AppTime(name, ms)
            }
            .sortedByDescending { it.ms }
    }
}
