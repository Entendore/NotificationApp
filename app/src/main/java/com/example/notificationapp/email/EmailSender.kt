package com.example.notificationapp.email

import android.util.Log
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object EmailSender {

    // TODO: Move to a backend or secure storage — never ship real credentials!
    // Gmail: enable 2-Step Verification → Account → Security → App Passwords (16 chars)
    private const val SENDER_EMAIL = "your_email@gmail.com"
    private const val APP_PASSWORD = "your_16_char_app_password"

    fun sendEmail(to: String, subject: String, body: String): Boolean {
        return try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(SENDER_EMAIL, APP_PASSWORD)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(SENDER_EMAIL))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                this.subject = subject
                setText(body)
            }

            Transport.send(message)
            true
        } catch (e: Exception) {
            Log.e("EmailSender", "Failed to send email", e)
            false
        }
    }
}
