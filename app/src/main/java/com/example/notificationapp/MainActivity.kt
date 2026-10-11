package com.example.notificationapp

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.notificationapp.calendar.CalendarManager
import com.example.notificationapp.data.ReminderEntity
import com.example.notificationapp.data.ReminderRepository
import com.example.notificationapp.email.EmailSender
import com.example.notificationapp.notifications.AlarmScheduler
import com.example.notificationapp.notifications.NotificationHelper
import com.example.notificationapp.notifications.NotificationWorker
import com.example.notificationapp.ui.ReminderAdapter
import com.example.notificationapp.ui.ReminderTouchCallback

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ReminderAdapter
    private val sdf = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Toast.makeText(this,
            if (result.values.all { it }) "Calendar access granted ✅"
            else "Calendar permission denied — events can't be managed",
            Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createChannels(this)
        requestNotificationPermission()
        requestCalendarPermission()

        setupReminderList()
        observeReminders()

        // ---------- Local ----------
        findViewById<Button>(R.id.btnImmediate).setOnClickListener {
            NotificationHelper.showNotification(this, "Hello!", "This is a local notification 🔔")
        }
        findViewById<Button>(R.id.btnScheduled).setOnClickListener {
            NotificationWorker.scheduleOneTime(this, "Scheduled Reminder", "Your 10-second reminder!", 10)
            Toast.makeText(this, "Scheduled for 10 seconds from now", Toast.LENGTH_SHORT).show()
        }

        // ---------- Reminder flow ----------
        findViewById<Button>(R.id.btnPickDateTime).setOnClickListener { askEventTitle() }
        findViewById<Button>(R.id.btnOpenCalendar).setOnClickListener {
            CalendarManager.openCalendarApp(this, System.currentTimeMillis())
        }
        findViewById<Button>(R.id.btnClearAlarms).setOnClickListener {
            lifecycleScope.launch { AlarmScheduler.clearAllAlarms(this@MainActivity) }
        }

        // ---------- Rich styles ----------
        findViewById<Button>(R.id.btnBigText).setOnClickListener {
            NotificationHelper.showBigText(this, "Big Text",
                "A very long message that expands when you pull the notification down — " +
                "great for emails, articles, and long messages!")
        }
        findViewById<Button>(R.id.btnBigPicture).setOnClickListener {
            NotificationHelper.showBigPicture(this, "New photo", "Check it out!", R.mipmap.ic_launcher)
        }
        findViewById<Button>(R.id.btnInbox).setOnClickListener {
            NotificationHelper.showInbox(this, "3 new emails",
                listOf("Alice: Meeting at 4 PM", "Bob: PR approved ✅", "Carol: Lunch tomorrow?"))
        }
        findViewById<Button>(R.id.btnMessaging).setOnClickListener { NotificationHelper.showMessaging(this) }
        findViewById<Button>(R.id.btnProgress).setOnClickListener {
            NotificationHelper.showProgressDemo(this, lifecycleScope)
        }

        // ---------- Email now ----------
        findViewById<Button>(R.id.btnEmail).setOnClickListener {
            val email = findViewById<EditText>(R.id.etEmail).text.toString().trim()
            if (email.isBlank()) {
                Toast.makeText(this, "Enter an email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val ok = EmailSender.sendEmail(email, "Test from MyApp", "Sent directly from the phone 📱")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, if (ok) "✅ Sent" else "❌ Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==================== LIST (Room Flow — auto-updating) ====================

    private fun setupReminderList() {
        val rv = findViewById<RecyclerView>(R.id.rvReminders)
        adapter = ReminderAdapter { item ->
            if (item.type == ReminderEntity.TYPE_CALENDAR) {
                CalendarManager.openCalendarApp(this, item.time)
            }
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        ItemTouchHelper(
            ReminderTouchCallback(
                context = this,
                adapter = adapter,
                onDragEnded = { persistOrder() },
                onSwiped = { item, position -> handleSwipe(item, position) }
            )
        ).attachToRecyclerView(rv)
    }

    private fun observeReminders() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ReminderRepository.flow(this@MainActivity).collect { list ->
                    adapter.submitList(list)
                    findViewById<RecyclerView>(R.id.rvReminders).visibility =
                        if (list.isEmpty()) View.GONE else View.VISIBLE
                    findViewById<TextView>(R.id.tvEmpty).visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun persistOrder() {
        lifecycleScope.launch {
            ReminderRepository.reindex(this@MainActivity, adapter.currentList)
        }
    }

    private fun handleSwipe(item: ReminderEntity, position: Int) {
        when (item.type) {

            ReminderEntity.TYPE_ALARM -> {
                lifecycleScope.launch { AlarmScheduler.deleteAlarm(this@MainActivity, item.id) }
                showUndoSnackbar("Reminder deleted") {
                    lifecycleScope.launch {
                        AlarmScheduler.scheduleAlarm(this@MainActivity, item.title, item.message, item.time)
                    }
                }
            }

            ReminderEntity.TYPE_EMAIL -> {
                lifecycleScope.launch {
                    EmailWorker.cancel(this@MainActivity, item.workName ?: "email_${item.id}")
                    ReminderRepository.delete(this@MainActivity, item.id)
                }
                showUndoSnackbar("Email reminder deleted") {
                    lifecycleScope.launch {
                        item.emailTo?.let {
                            ReminderRepository.insertEmail(
                                this@MainActivity, it, item.title, item.emailBody ?: "", item.time)
                        }
                    }
                }
            }

            ReminderEntity.TYPE_CALENDAR -> {
                AlertDialog.Builder(this)
                    .setTitle("Delete event?")
                    .setMessage("Remove \"${item.title}\" from Google Calendar?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            item.calendarEventId?.let {
                                CalendarManager.deleteEvent(this@MainActivity, it)
                            }
                            ReminderRepository.delete(this@MainActivity, item.id)
                        }
                        showUndoSnackbar("Event deleted") {
                            lifecycleScope.launch {
                                val cal = CalendarManager.getCalendars(this@MainActivity).firstOrNull()
                                if (cal != null) {
                                    val newId = CalendarManager.addEvent(
                                        this@MainActivity, cal.id, item.title, item.message,
                                        item.time, item.time + 3_600_000L, 0)
                                    ReminderRepository.insertCalendar(
                                        this@MainActivity, item.title, item.time, newId ?: 0L)
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel") { _, _ -> adapter.notifyItemChanged(position) }
                    .setOnCancelListener { adapter.notifyItemChanged(position) }
                    .show()
            }
        }
    }

    private fun showUndoSnackbar(message: String, undo: () -> Unit) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
            .setAction("UNDO") { undo() }
            .show()
    }

    // ==================== REMINDER FLOW ====================

    private fun askEventTitle() {
        val input = EditText(this).apply {
            hint = "e.g. Team meeting, Doctor appointment…"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Reminder title")
            .setView(input)
            .setPositiveButton("Next") { _, _ -> pickDateTime(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickDateTime(title: String) {
        val eventTitle = title.ifBlank { "Reminder" }
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, min ->
                cal.set(y, m, d, h, min, 0)
                cal.set(Calendar.MILLISECOND, 0)
                chooseReminderOffset(eventTitle, cal.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun chooseReminderOffset(eventTitle: String, eventTime: Long) {
        val options = arrayOf("At start time", "10 min before", "30 min before", "1 hour before")
        val offsets = intArrayOf(0, 10, 30, 60)
        AlertDialog.Builder(this)
            .setTitle("Remind me…")
            .setItems(options) { _, which ->
                ensureExactAlarm()
                deliveryOptions(eventTitle, eventTime, offsets[which])
            }
            .show()
    }

    private fun deliveryOptions(eventTitle: String, eventTime: Long, offsetMinutes: Int) {
        val choices = arrayOf(
            "🔔 Local notification (this phone)",
            "📅 Add to Google Calendar",
            "📧 Send me an email reminder"
        )
        val checked = booleanArrayOf(true, false, false)

        AlertDialog.Builder(this)
            .setTitle("Delivery")
            .setMultiChoiceItems(choices, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Done") { _, _ ->
                ensureExactAlarm()
                val reminders = if (offsetMinutes == 0) listOf(0) else listOf(offsetMinutes, 0)
                if (checked[0]) {
                    lifecycleScope.launch {
                        AlarmScheduler.scheduleEventReminders(
                            this@MainActivity, eventTitle, eventTime, reminders)
                    }
                }
                if (checked[1]) addToGoogleCalendar(eventTitle, eventTime, offsetMinutes)
                if (checked[2]) askEmailAndSchedule(eventTitle, eventTime, offsetMinutes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addToGoogleCalendar(eventTitle: String, eventTime: Long, offsetMinutes: Int) {
        if (!CalendarManager.hasPermission(this)) {
            requestCalendarPermission()
            Toast.makeText(this, "Grant calendar permission and try again", Toast.LENGTH_LONG).show()
            return
        }
        val calendars = CalendarManager.getCalendars(this)
        if (calendars.isEmpty()) {
            Toast.makeText(this,
                "No writable calendars found. Add a Google account on this device first.",
                Toast.LENGTH_LONG).show()
            return
        }
        val names = calendars.map { "${it.name} (${it.accountName})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Save to which calendar?")
            .setItems(names) { _, which ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val eventId = CalendarManager.addEvent(
                        this@MainActivity, calendars[which].id, eventTitle,
                        "Created by NotificationApp",
                        eventTime, eventTime + 60 * 60_000L, offsetMinutes)
                    if (eventId != null) {
                        ReminderRepository.insertCalendar(this@MainActivity, eventTitle, eventTime, eventId)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity,
                            if (eventId != null) "Saved to Google Calendar ✅" else "Could not save ❌",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun askEmailAndSchedule(eventTitle: String, eventTime: Long, offsetMinutes: Int) {
        val input = EditText(this).apply {
            hint = "you@example.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        AlertDialog.Builder(this)
            .setTitle("Email reminder to…")
            .setView(input)
            .setPositiveButton("Schedule email") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isBlank()) return@setPositiveButton

                val sendAt = eventTime - offsetMinutes * 60_000L
                lifecycleScope.launch {
                    ReminderRepository.insertEmail(
                        this@MainActivity, email,
                        subject = "⏰ Reminder: $eventTitle",
                        body = "Your event \"$eventTitle\" starts at ${sdf.format(Date(eventTime))}.",
                        sendAt = sendAt
                    )
                    Toast.makeText(this@MainActivity, "Email scheduled 📧", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==================== Permissions ====================

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestCalendarPermission() {
        if (!CalendarManager.hasPermission(this)) {
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    private fun ensureExactAlarm() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            )
            Toast.makeText(this, "Allow 'Alarms & reminders', then create the reminder again", Toast.LENGTH_LONG).show()
        }
    }
}
