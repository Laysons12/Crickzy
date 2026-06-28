package com.example.crickzy.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.crickzy.R
import com.example.crickzy.database.SupabaseHelper
import com.example.crickzy.models.Booking
import com.example.crickzy.utils.NotificationHelper
import com.google.android.material.textfield.TextInputEditText
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class TurfBookingActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 101

    private var turfId: Long = -1
    private var turfName = ""
    private var turfLoc = ""
    private var turfPrice = 0.0
    private var turfImg = ""

    private var selectedDate = ""
    private var selectedTime = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_turf_booking)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        turfId = intent.getLongExtra("TURF_ID", -1)
        turfName = intent.getStringExtra("TURF_NAME") ?: "Unknown Turf"
        turfLoc = intent.getStringExtra("TURF_LOC") ?: "Unknown Location"
        turfPrice = intent.getDoubleExtra("TURF_PRICE", 0.0)
        turfImg = intent.getStringExtra("TURF_IMG") ?: ""

        val ivPhoto = findViewById<ImageView>(R.id.ivBookingTurfImage)
        val tvName = findViewById<TextView>(R.id.tvBookingTurfName)
        val tvLoc = findViewById<TextView>(R.id.tvBookingTurfLoc)
        val tvPrice = findViewById<TextView>(R.id.tvBookingTurfPrice)

        ivPhoto.load(turfImg) {
            crossfade(true)
            placeholder(R.drawable.ic_cricket_logo)
            error(R.drawable.ic_cricket_logo)
        }
        tvName.text = turfName
        tvLoc.text = "Location: $turfLoc"
        tvPrice.text = "Price: ₹$turfPrice/hr"

        val tvPreview = findViewById<TextView>(R.id.tvBookingDateTimePreview)
        val btnDate = findViewById<Button>(R.id.btnSelectBookingDate)
        val btnTime = findViewById<Button>(R.id.btnSelectBookingTime)
        val rgArena = findViewById<RadioGroup>(R.id.rgArenaType)
        val etPhone = findViewById<TextInputEditText>(R.id.etBookingPhone)
        val btnConfirm = findViewById<Button>(R.id.btnConfirmBooking)

        btnDate.setOnClickListener {
            val cal = Calendar.getInstance()
            val datePicker = DatePickerDialog(this, { _, y, m, d ->
                selectedDate = "$d/${m + 1}/$y"
                updatePreview(tvPreview)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            datePicker.datePicker.minDate = cal.timeInMillis
            datePicker.show()
        }

        val timeSlots = arrayOf(
            "6:00 AM", "7:30 AM", "9:00 AM", "10:30 AM",
            "12:00 PM", "1:30 PM", "3:00 PM", "4:30 PM",
            "6:00 PM", "7:30 PM"
        )

        btnTime.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Select Time Slot")
                .setItems(timeSlots) { _, which ->
                    selectedTime = timeSlots[which]
                    updatePreview(tvPreview)
                }
                .show()
        }

        btnConfirm.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            if (phone.isEmpty() || selectedDate.isEmpty() || selectedTime.isEmpty()) {
                Toast.makeText(this, "Please select Date, Time, and enter Phone Number!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val digitsOnly = phone.replace(Regex("[^0-9]"), "")
            if (digitsOnly.length < 10) {
                Toast.makeText(this, "Please enter a valid phone number (at least 10 digits)!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedArenaId = rgArena.checkedRadioButtonId
            if (selectedArenaId == -1) {
                Toast.makeText(this, "Please select an arena type!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val arenaType = findViewById<RadioButton>(selectedArenaId).text.toString()

            val booking = Booking(turfId = turfId, date = selectedDate, time = selectedTime, userId = -1)

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { SupabaseHelper.addBooking(booking) }

                    val smsMessage = "Crickzy Booking Confirmed! $turfName ($arenaType) on $selectedDate at $selectedTime. Location: $turfLoc. Price: Rs.$turfPrice/hr. Enjoy your game!"
                    
                    if (ContextCompat.checkSelfPermission(this@TurfBookingActivity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        sendSmsAndFinish(phone, smsMessage)
                    } else {
                        ActivityCompat.requestPermissions(this@TurfBookingActivity, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
                        selectedSmsPhone = phone
                        selectedSmsMessage = smsMessage
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TurfBookingActivity", "Booking failed: ${e.message}", e)
                    Toast.makeText(this@TurfBookingActivity, "Booking failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var selectedSmsPhone = ""
    private var selectedSmsMessage = ""

    private fun sendSmsAndFinish(phone: String, message: String) {
        NotificationHelper.sendSms(this, phone, message)
        Toast.makeText(this, "Turf Booked! Sending SMS to $phone...", Toast.LENGTH_LONG).show()
        window.decorView.postDelayed({ finish() }, 2000)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendSmsAndFinish(selectedSmsPhone, selectedSmsMessage)
            } else {
                Toast.makeText(this, "Turf Booked! Failed to send SMS (Permission denied).", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun updatePreview(tv: TextView) {
        if (selectedDate.isNotEmpty() && selectedTime.isNotEmpty()) {
            tv.text = "Selected: $selectedDate at $selectedTime"
        } else if (selectedDate.isNotEmpty()) {
            tv.text = "Selected: $selectedDate, Time not set"
        } else if (selectedTime.isNotEmpty()) {
            tv.text = "Selected: Time $selectedTime, Date not set"
        }
    }
}
