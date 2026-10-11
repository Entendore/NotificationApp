package com.example.notificationapp.email

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import com.example.notificationapp.data.ReminderRepository

class EmailWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getInt("dbId", -1)
        val to = inputData.getString("to") ?: return Result.failure()
        val subject = inputData.getString("subject") ?: "Reminder"
        val body = inputData.getString("body") ?: ""

        return if (EmailSender.sendEmail(to, subject, body)) {
            ReminderRepository.delete(applicationContext, id)   // Flow removes the row
            Result.success()
        } else Result.retry()
    }

    companion object {
        fun enqueue(
            context: Context, dbId: Int, to: String, subject: String, body: String, sendAt: Long
        ) {
            val delay = (sendAt - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<EmailWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(
                    "dbId" to dbId, "to" to to, "subject" to subject, "body" to body))
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("email_$dbId", ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, workName: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName)
        }
    }
}
