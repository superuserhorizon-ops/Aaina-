package app.aaina

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object Notifier {
    private const val CH = "slip"

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Notification for yesterday's slip. Built from live usage data and not stored. */
    @SuppressLint("MissingPermission")
    fun post(ctx: Context) {
        if (!canPost(ctx) || !Usage.hasAccess(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Daily slip", NotificationManager.IMPORTANCE_DEFAULT))
        val p = Prefs(ctx)
        val week = (6 downTo 0).map { Usage.forDay(ctx, it) }
        val total = week[5].sumOf { it.ms }
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).putExtra("yesterday", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val publicVersion = NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Daily summary").setContentText("Unlock your phone to view").build()
        val b = NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_stat).setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
        if (p.discreet) {
            b.setContentTitle("Daily summary").setContentText("Tap to view your slip")
        } else {
            val label = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("d MMM"))
            val under = total <= p.limit * 60000L
            b.setContentTitle("Your slip for $label is ready")
                .setContentText("Total ${SlipRenderer.fmt(total)}. " + if (under) "You stayed under your limit." else "You went over your limit.")
            val bmp = SlipBuilder.build(ctx, week, 5, p.effTheme(), p.color, p.limit, p.hidden, false, true)
            b.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp))
        }
        NotificationManagerCompat.from(ctx).notify(1, b.build())
    }

    /** Runs once a day shortly after 12:00 AM. Android may delay it by a few minutes. */
    fun schedule(ctx: Context, replace: Boolean) {
        val now = LocalDateTime.now()
        val next = now.toLocalDate().plusDays(1).atTime(0, 5)
        val delay = Duration.between(now, next).toMillis()
        val req = PeriodicWorkRequestBuilder<MidnightWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "midnight", if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req
        )
    }

    fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork("midnight") }
}

class MidnightWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        Notifier.post(applicationContext)
        return Result.success()
    }
}
