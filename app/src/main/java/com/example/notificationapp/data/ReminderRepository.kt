package com.example.notificationapp.data

import android.content.Context
import com.example.notificationapp.email.EmailWorker

object ReminderRepository {

    private fun dao(context: Context) = ReminderDatabase.get(context).reminderDao()

    /** Live stream — the Activity just collects this */
    fun flow(context: Context) = dao(context).getAllFlow()

    suspend fun get(context: Context, id: Int) = dao(context).getById(id)
    suspend fun delete(context: Context, id: Int) = dao(context).deleteById(id)
    suspend fun reindex(context: Context, ordered: List<ReminderEntity>) =
        dao(context).reindex(ordered)

    /** New items slot into their time-sorted position using fractional ordering */
    private suspend fun orderFor(context: Context, time: Long): Double {
        val list = dao(context).getAll()
        val prev = list.lastOrNull { it.time <= time }
        val next = list.firstOrNull { it.time > time }
        return when {
            prev == null && next == null -> 0.0
            prev == null -> next!!.sortOrder - 1.0
            next == null -> prev.sortOrder + 1.0
            else -> (prev.sortOrder + next.sortOrder) / 2.0
        }
    }

    suspend fun insertAlarm(context: Context, title: String, message: String, time: Long): Int =
        dao(context).insert(
            ReminderEntity(
                type = ReminderEntity.TYPE_ALARM, title = title,
                message = message, time = time, sortOrder = orderFor(context, time)
            )
        ).toInt()

    suspend fun insertCalendar(context: Context, title: String, time: Long, eventId: Long): Int =
        dao(context).insert(
            ReminderEntity(
                type = ReminderEntity.TYPE_CALENDAR, title = title,
                message = "Google Calendar event", time = time,
                sortOrder = orderFor(context, time), calendarEventId = eventId
            )
        ).toInt()

    suspend fun insertEmail(
        context: Context, to: String, subject: String, body: String, sendAt: Long
    ): Int {
        val dao = dao(context)
        val id = dao.insert(
            ReminderEntity(
                type = ReminderEntity.TYPE_EMAIL, title = subject,
                message = "→ $to", time = sendAt, sortOrder = orderFor(context, sendAt),
                emailTo = to, emailBody = body
            )
        ).toInt()

        val workName = "email_$id"
        dao.getById(id)?.let { dao.update(it.copy(workName = workName)) }
        EmailWorker.enqueue(context, id, to, subject, body, sendAt)
        return id
    }
}
