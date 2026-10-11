package com.example.notificationapp.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

object CalendarManager {

    data class DeviceCalendar(val id: Long, val name: String, val accountName: String)

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
            == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            == PackageManager.PERMISSION_GRANTED

    /** Writable calendars (e.g. the user's Google account calendars on this phone) */
    fun getCalendars(context: Context): List<DeviceCalendar> {
        val out = mutableListOf<DeviceCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        val selection =
            "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
            CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(DeviceCalendar(c.getLong(0), c.getString(1), c.getString(2)))
                }
            }
        } catch (_: SecurityException) { }
        return out
    }

    /**
     * Insert an event into the phone's calendar (Google Calendar picks it up on next sync).
     * Returns the new event ID, or null on failure.
     */
    fun addEvent(
        context: Context,
        calendarId: Long,
        title: String,
        description: String,
        startMillis: Long,
        endMillis: Long,
        reminderMinutes: Int
    ): Long? {
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }

            val eventUri = context.contentResolver
                .insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
            val eventId = eventUri.lastPathSegment?.toLongOrNull() ?: return null

            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)
            eventId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteEvent(context: Context, eventId: Long): Boolean = try {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.delete(uri, null, null) > 0
    } catch (_: SecurityException) { false }

    fun openCalendarApp(context: Context, timeMillis: Long) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time").appendPath(timeMillis.toString()).build()
        }
        runCatching { context.startActivity(intent) }
    }
}
