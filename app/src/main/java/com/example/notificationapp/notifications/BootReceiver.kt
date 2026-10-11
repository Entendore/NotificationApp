package com.example.notificationapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val result = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try { AlarmScheduler.rescheduleAll(context) } finally { result.finish() }
            }
        }
    }
}
