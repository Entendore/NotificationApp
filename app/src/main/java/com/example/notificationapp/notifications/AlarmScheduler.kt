package com.example.notificationapp.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.notificationapp.data.ReminderDatabase
import com.example.notificationapp.data.ReminderEntity
import com.example.notificationapp.data.ReminderRepository

object AlarmScheduler {

    /** Registers the system alarm (Room row id = request code) */
    fun register(context: Context, id: Int, title: String, message: String, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        val pi = pendingIntent(context, id, title, message)
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 10 * 60_000L, pi)
    }

    fun cancel(context: Context, id: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, id, "", ""))
    }

    private fun pendingIntent(context: Context, id: Int, title: String, message: String) =
        PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java).apply {
                putExtra("id", id); putExtra("title", title); putExtra("message", message)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ---- Orchestration (Room + system alarm together) ----

    suspend fun scheduleAlarm(context: Context, title: String, message: String, time: Long) {
        val id = ReminderRepository.insertAlarm(context, title, message, time)
        register(context, id, title, message, time)
    }

    suspend fun scheduleEventReminders(
        context: Context, title: String, eventTime: Long, minutesBefore: List<Int>
    ) {
        minutesBefore.forEach { mins ->
            val msg = if (mins == 0) "Starting now!" else "Starts in $mins min"
            scheduleAlarm(context, title, msg, eventTime - mins * 60_000L)
        }
    }

    suspend fun snooze(context: Context, id: Int, minutes: Long = 10) {
        val row = ReminderRepository.get(context, id) ?: return
        val newTime = System.currentTimeMillis() + minutes * 60_000L
        ReminderDatabase.get(context).reminderDao().updateTime(id, newTime)
        register(context, id, row.title, row.message, newTime)
    }

    suspend fun deleteAlarm(context: Context, id: Int) {
        cancel(context, id)
        ReminderRepository.delete(context, id)
    }

    suspend fun clearAllAlarms(context: Context) {
        val dao = ReminderDatabase.get(context).reminderDao()
        dao.getByType(ReminderEntity.TYPE_ALARM).forEach {
            cancel(context, it.id)
            dao.deleteById(it.id)
        }
    }

    /** BootReceiver: re-register every ALARM row still in the future */
    suspend fun rescheduleAll(context: Context) {
        val dao = ReminderDatabase.get(context).reminderDao()
        dao.getByType(ReminderEntity.TYPE_ALARM).forEach { row ->
            if (row.time > System.currentTimeMillis()) {
                register(context, row.id, row.title, row.message, row.time)
            } else {
                cancel(context, row.id)
                dao.deleteById(row.id)
            }
        }
    }
}
