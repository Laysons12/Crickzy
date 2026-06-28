package com.example.crickzy.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast

object NotificationHelper {

    private const val SMS_SENT_ACTION = "com.example.crickzy.SMS_SENT"
    private const val SMS_DELIVERED_ACTION = "com.example.crickzy.SMS_DELIVERED"

    fun sendSms(context: Context, phoneNumber: String, message: String) {
        try {
            // Format phone number - ensure it has country code
            val formattedNumber = formatPhoneNumber(phoneNumber)

            // Get the correct SmsManager based on API level
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Create PendingIntent for sent confirmation
            val sentIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(SMS_SENT_ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Create PendingIntent for delivery confirmation
            val deliveredIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(SMS_DELIVERED_ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Register broadcast receiver for sent status
            val sentReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (resultCode) {
                        Activity.RESULT_OK ->
                            Toast.makeText(context, "SMS sent successfully to $formattedNumber!", Toast.LENGTH_LONG).show()
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                            Toast.makeText(context, "SMS failed: Generic failure. Check your SIM/network.", Toast.LENGTH_LONG).show()
                        SmsManager.RESULT_ERROR_NO_SERVICE ->
                            Toast.makeText(context, "SMS failed: No cellular service available.", Toast.LENGTH_LONG).show()
                        SmsManager.RESULT_ERROR_NULL_PDU ->
                            Toast.makeText(context, "SMS failed: Null PDU error.", Toast.LENGTH_LONG).show()
                        SmsManager.RESULT_ERROR_RADIO_OFF ->
                            Toast.makeText(context, "SMS failed: Radio/Airplane mode is ON.", Toast.LENGTH_LONG).show()
                        else ->
                            Toast.makeText(context, "SMS failed with unknown error code: $resultCode", Toast.LENGTH_LONG).show()
                    }
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}
                }
            }

            // Register broadcast receiver for delivery status
            val deliveredReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (resultCode) {
                        Activity.RESULT_OK ->
                            Toast.makeText(context, "SMS delivered to receiver!", Toast.LENGTH_SHORT).show()
                        Activity.RESULT_CANCELED ->
                            Toast.makeText(context, "SMS not delivered yet.", Toast.LENGTH_SHORT).show()
                    }
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(sentReceiver, IntentFilter(SMS_SENT_ACTION), Context.RECEIVER_NOT_EXPORTED)
                context.registerReceiver(deliveredReceiver, IntentFilter(SMS_DELIVERED_ACTION), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(sentReceiver, IntentFilter(SMS_SENT_ACTION))
                context.registerReceiver(deliveredReceiver, IntentFilter(SMS_DELIVERED_ACTION))
            }

            // Check if message is too long for single SMS (160 chars) and split if needed
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentIntents.add(sentIntent)
                    deliveredIntents.add(deliveredIntent)
                }
                smsManager.sendMultipartTextMessage(
                    formattedNumber,
                    null,
                    parts,
                    sentIntents,
                    deliveredIntents
                )
            } else {
                smsManager.sendTextMessage(
                    formattedNumber,
                    null,
                    message,
                    sentIntent,
                    deliveredIntent
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "SMS sending failed: ${e.message}. Opening SMS app as fallback...",
                Toast.LENGTH_LONG
            ).show()
            // Fallback: open the SMS app with pre-filled content
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:${formatPhoneNumber(phoneNumber)}")
                    putExtra("sms_body", message)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "No SMS app found on device.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Formats phone number to ensure it includes country code.
     * If number starts with 0, replaces with +91 (India).
     * If number doesn't start with +, prepends +91.
     */
    private fun formatPhoneNumber(phoneNumber: String): String {
        var number = phoneNumber.trim().replace(" ", "").replace("-", "")
        if (number.startsWith("0")) {
            number = "+91" + number.substring(1)
        } else if (!number.startsWith("+")) {
            number = "+91$number"
        }
        return number
    }

    fun sendEmail(context: Context, emailAddress: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "No Email app found", Toast.LENGTH_SHORT).show()
        }
    }
}
