package com.example.notificationapp.notifications

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val title = inputData.getString("title") ?: "Reminder"
        val message = inputData.getString("message") ?: "You have a notification"
        NotificationHelper.showNotification(applicationContext, title, message)
        return Result.success()
    }

    companion object {
        fun scheduleOneTime(
            context: Context, title: String, message: String, delaySeconds: Long
        ) {
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setInputData(workDataOf("title" to title, "message" to message))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
