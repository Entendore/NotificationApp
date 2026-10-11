package com.example.notificationapp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.notificationapp.MainActivity
import com.example.notificationapp.R

object NotificationHelper {

    private const val CHANNEL_GENERAL = "general_channel"
    private const val CHANNEL_CALENDAR = "calendar_channel"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_GENERAL, "General", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "General notifications" }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_CALENDAR, "Calendar Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Event reminders, like Google Calendar"
                    enableVibration(true)
                }
            )
        }
    }

    // ---------- Local notification ----------

    fun showNotification(context: Context, title: String, message: String, id: Int = 1001) {
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = base(context, CHANNEL_GENERAL, title, message).setContentIntent(pi).build()
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, n)
    }

    // ---------- Calendar alarm notification with Snooze / Dismiss ----------

    fun showAlarmNotification(context: Context, id: Int, title: String, message: String) {
        val openApp = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze = PendingIntent.getBroadcast(
            context, id + 100_000,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_SNOOZE
                putExtra("id", id); putExtra("title", title); putExtra("message", message)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismiss = PendingIntent.getBroadcast(
            context, id + 200_000,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_DISMISS
                putExtra("id", id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, CHANNEL_CALENDAR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(0, "⏰ Snooze 10 min", snooze)
            .addAction(0, "✕ Dismiss", dismiss)
            .build()

        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, n)
    }

    // ---------- Rich styles ----------

    fun showBigText(context: Context, title: String, longText: String, id: Int = 2001) {
        val n = base(context, CHANNEL_GENERAL, title, longText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(longText))
            .build()
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, n)
    }

    fun showBigPicture(
        context: Context, title: String, text: String,
        @DrawableRes imageRes: Int, id: Int = 2002
    ) {
        val n = base(context, CHANNEL_GENERAL, title, text)
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(toBitmap(context, imageRes)))
            .setLargeIcon(toBitmap(context, imageRes))
            .build()
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, n)
    }

    fun showInbox(context: Context, title: String, lines: List<String>, id: Int = 2003) {
        val style = NotificationCompat.InboxStyle().setSummaryText("${lines.size} new items")
        lines.forEach { style.addLine(it) }
        val n = base(context, CHANNEL_GENERAL, title, lines.first())
            .setStyle(style)
            .setNumber(lines.size)
            .build()
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, n)
    }

    fun showMessaging(context: Context, id: Int = 2004) {
        val me = android.app.Person.Builder().setName("You").build()
        val alice = android.app.Person.Builder().setName("Alice").build()
        val now = System.currentTimeMillis()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle("Project Chat")
            .addMessage("Hey! Are we still on for 3 PM?", now - 300_000, alice)
            .addMessage("Yes, sending the agenda now 📎", now - 200_000, me)
            .addMessage("Perfect, see you then!", now - 100_000, alice)
        val n = base(context, CHANNEL_GENERAL, "New messages", "Alice: Perfect, see you then!")
            .setStyle(style)
            .build()
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, n)
    }

    fun showProgressDemo(context: Context, scope: CoroutineScope, id: Int = 3001) {
        if (!canPost(context)) return
        val nm = NotificationManagerCompat.from(context)
        val builder = base(context, CHANNEL_GENERAL, "Downloading report.pdf…", "Please wait")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, false)

        scope.launch {
            for (p in 0..100 step 5) {
                builder.setProgress(100, p, false).setSubText("$p%")
                nm.notify(id, builder.build())
                delay(120)
            }
            builder.setContentText("Download complete ✅")
                .setProgress(0, 0, false)
                .setOngoing(false)
            nm.notify(id, builder.build())
        }
    }

    // ---------- Helpers ----------

    private fun base(context: Context, channelId: String, title: String, text: String) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

    private fun canPost(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED

    private fun toBitmap(context: Context, @DrawableRes res: Int): Bitmap {
        val d = ContextCompat.getDrawable(context, res)!!
        if (d is BitmapDrawable) return d.bitmap
        val bmp = Bitmap.createBitmap(d.intrinsicWidth, d.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, canvas.width, canvas.height)
        d.draw(canvas)
        return bmp
    }
}
