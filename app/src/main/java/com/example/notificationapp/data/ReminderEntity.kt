package com.example.notificationapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,                // TYPE_ALARM | TYPE_CALENDAR | TYPE_EMAIL
    val title: String,
    val message: String,             // row subtitle
    val time: Long,
    val sortOrder: Double = 0.0,     // drag-reorder position
    // Email rows only
    val emailTo: String? = null,
    val emailBody: String? = null,
    val workName: String? = null,
    // Calendar rows only
    val calendarEventId: Long? = null
) {
    companion object {
        const val TYPE_ALARM = "ALARM"
        const val TYPE_CALENDAR = "CALENDAR"
        const val TYPE_EMAIL = "EMAIL"
    }
}
