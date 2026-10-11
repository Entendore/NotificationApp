# NotificationApp — Local Notification & Reminder App

Fully local (no Firebase): local/scheduled notifications, rich styles,
exact-alarm reminders with Snooze/Dismiss, Google Calendar events,
scheduled SMTP email reminders, Room database, RecyclerView with
swipe-to-delete (UNDO) + drag-to-reorder.

## Before running
1. Open this folder in **Android Studio** (JDK 17 required).
   If the Gradle wrapper is missing, use the IDE's "Fix Gradle wrapper" prompt,
   or run: `gradle wrapper --gradle-version 8.5`
2. Edit `app/src/main/java/com.example.notificationapp/email/EmailSender.kt`:
   set your Gmail address + 16-char **App Password**
   (Google Account → Security → 2-Step Verification → App Passwords).
3. Run on a real device. Grant: Notifications, Calendar, and
   Settings → Apps → this app → Alarms & reminders (Android 12+).

## Test
- Create a reminder 2–3 min ahead → lock screen → notification fires on time.
- Reboot → reminder still fires (BootReceiver + Room).
- Snooze/Dismiss from the notification.
- Long-press a row to drag-reorder; swipe left/right to delete (UNDO works).
