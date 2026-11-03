package com.example.midad.notify

import android.Manifest
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.midad.MainActivity
import com.example.midad.MidadApp
import com.example.midad.core.Statements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * التذكيرات المحلية — كل شيء على الجهاز، بلا خادم ولا إشعارات سحابية.
 *
 * ما يُذكَّر به يومياً:
 *  · شيكات مستلمة/مودعة تستحق خلال ٣ أيام
 *  · فواتير دورية مستحقة أو متأخرة
 *  · مستندات غير محصّلة تجاوزت ٣٠ يوماً
 *
 * الإشعار إرشادي فقط؛ لا يغيّر أي حالة محاسبية.
 */
object Reminders {

    const val CHANNEL_ID = "midad_daily_reminders"
    const val WORK_NAME = "midad-daily-reminders"
    private const val NOTIFICATION_ID = 4101

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "تذكيرات TAMM اليومية", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "شيكات وفواتير دورية ومستحقات تحتاج متابعة"
            },
        )
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, title: String, body: String) {
        ensureChannel(ctx)
        if (!canNotify(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
    }

    /** جدولة العمل اليومي — `KEEP` لا يُكرّر الجدولة عند كل تشغيل للتطبيق */
    fun schedule(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
    }
}

/** يبني نص التذكير من بيانات قاعدة البيانات — منطق النصوص هنا فقط، والحسابات في النواة */
object ReminderText {

    fun build(chequesDue: Int, chequesValue: Long, schedulesDue: List<String>, staleDocs: Int, currency: String): Pair<String, String>? {
        val parts = mutableListOf<String>()
        if (chequesDue > 0) {
            parts += "شيكات تستحق خلال ٣ أيام: $chequesDue (${com.example.midad.core.Money.format(chequesValue, currency)})"
        }
        if (schedulesDue.isNotEmpty()) {
            parts += "فواتير دورية مستحقة: ${schedulesDue.size} — ${schedulesDue.take(3).joinToString("، ")}"
        }
        if (staleDocs > 0) {
            parts += "مستندات آجلة تجاوزت ٣٠ يوماً: $staleDocs"
        }
        if (parts.isEmpty()) return null
        return "TAMM · متابعة اليوم" to parts.joinToString("\n")
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext as MidadApp
        val dao = app.db.erpDao()
        val company = MidadApp.COMPANY_ID
        val today = LocalDate.now().toString()

        val settings = withContext(Dispatchers.IO) { dao.settings().associate { it.key to it.value } }
        if (settings["reminders"] == "0") return Result.success()

        val cheques = withContext(Dispatchers.IO) { dao.cheques(company) }
        val dueCheques = cheques.filter {
            (it.status == "RECEIVED" || it.status == "DEPOSITED") &&
                Statements.daysBetween(today, it.dueDate) in 0..3
        }
        val schedules = withContext(Dispatchers.IO) { dao.schedules(company) }
        val schedulesDue = schedules.filter { sch ->
            if (!sch.active) return@filter false
            val cycle = runCatching { com.example.midad.core.Recurring.Cycle.valueOf(sch.cycle) }
                .getOrDefault(com.example.midad.core.Recurring.Cycle.MONTHLY)
            val base = sch.lastRunDate ?: sch.startDate
            val next = com.example.midad.core.Recurring.nextDate(base, cycle, sch.dayOfMonth)
            next <= today && (sch.endDate == null || next <= sch.endDate)
        }.map { it.title }
        val docs = withContext(Dispatchers.IO) { dao.documents(company) }
        val staleDocs = docs.count {
            it.status == "CONFIRMED" && it.paidMinor < it.totalMinor &&
                Statements.daysBetween(today, it.date) > 30
        }

        ReminderText.build(
            chequesDue = dueCheques.size,
            chequesValue = dueCheques.sumOf { it.amountMinor },
            schedulesDue = schedulesDue,
            staleDocs = staleDocs,
            currency = settings["currency"] ?: "YER",
        )?.let { (title, body) ->
            Reminders.show(applicationContext, title, body)
        }
        Result.success()
    }.getOrElse { Result.success() }
}
