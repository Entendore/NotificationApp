package com.example.notificationapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.notificationapp.data.ReminderRepository

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 1001)
        val title = intent.getStringExtra("title") ?: "Reminder"
        val message = intent.getStringExtra("message") ?: ""

        when (intent.action) {
            ACTION_SNOOZE -> {
                NotificationManagerCompat.from(context).cancel(id)
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { AlarmScheduler.snooze(context, id) } finally { result.finish() }
                }
            }
            ACTION_DISMISS -> {
                NotificationManagerCompat.from(context).cancel(id)
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AlarmScheduler.cancel(context, id)
                        ReminderRepository.delete(context, id)
                    } finally { result.finish() }
                }
            }
            else -> NotificationHelper.showAlarmNotification(context, id, title, message)
        }
    }

    companion object {
        const val ACTION_SNOOZE = "app.action.SNOOZE"
        const val ACTION_DISMISS = "app.action.DISMISS"
    }
}
