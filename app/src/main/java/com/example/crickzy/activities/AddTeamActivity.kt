package com.example.crickzy.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.crickzy.R
import com.example.crickzy.database.SupabaseHelper
import com.example.crickzy.models.Team
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class AddTeamActivity : AppCompatActivity() {

    private lateinit var etTeamName: TextInputEditText
    private lateinit var etTeamLocation: TextInputEditText
    private lateinit var etTeamArea: TextInputEditText
    private lateinit var etTeamPhone: TextInputEditText
    private lateinit var etTeamWhatsapp: TextInputEditText
    private lateinit var spinnerReqRole: Spinner
    private lateinit var rgTeamBallType: RadioGroup
    private lateinit var switchNeedsPlayers: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var btnTeamDate: Button
    private lateinit var btnTeamTime: Button
    private lateinit var tvTeamDateTime: TextView
    private lateinit var btnSaveTeam: Button
    private lateinit var btnSelectTeamLogo: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var ivTeamLogo: ImageView

    private var selectedDate = ""
    private var selectedTime = ""
    private var selectedImageUri = "default"

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri.toString()
            ivTeamLogo.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_team)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etTeamName = findViewById(R.id.etTeamName)
        etTeamLocation = findViewById(R.id.etTeamLocation)
        etTeamArea = findViewById(R.id.etTeamArea)
        etTeamPhone = findViewById(R.id.etTeamPhone)
        etTeamWhatsapp = findViewById(R.id.etTeamWhatsapp)
        spinnerReqRole = findViewById(R.id.spinnerReqRole)
        rgTeamBallType = findViewById(R.id.rgTeamBallType)
        switchNeedsPlayers = findViewById(R.id.switchNeedsPlayers)
        btnTeamDate = findViewById(R.id.btnTeamDate)
        btnTeamTime = findViewById(R.id.btnTeamTime)
        tvTeamDateTime = findViewById(R.id.tvTeamDateTime)
        btnSaveTeam = findViewById(R.id.btnSaveTeam)
        btnSelectTeamLogo = findViewById(R.id.btnSelectTeamLogo)
        ivTeamLogo = findViewById(R.id.ivTeamLogo)

        val roles = arrayOf("Batsman", "Bowler", "All-Rounder", "Wicket-Keeper")
        val adapter = ArrayAdapter(this, R.layout.item_spinner, roles)
        adapter.setDropDownViewResource(R.layout.item_spinner)
        spinnerReqRole.adapter = adapter
    }

    private fun setupListeners() {
        btnTeamDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val dpd = DatePickerDialog(this, { _, year, month, day ->
                selectedDate = "$day/${month + 1}/$year"
                updateDateTimePreview()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            dpd.datePicker.minDate = calendar.timeInMillis
            dpd.show()
        }

        btnTeamTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                selectedTime = String.format("%02d:%02d", hour, minute)
                updateDateTimePreview()
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        btnSaveTeam.setOnClickListener {
            saveTeam()
        }

        btnSelectTeamLogo.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun updateDateTimePreview() {
        tvTeamDateTime.text = "Selected: Date: $selectedDate | Time: $selectedTime"
    }

    private fun saveTeam() {
        val name = etTeamName.text.toString().trim()
        val loc = etTeamLocation.text.toString().trim()
        val area = etTeamArea.text.toString().trim()
        val phone = etTeamPhone.text.toString().trim()
        val whatsapp = etTeamWhatsapp.text.toString().trim()
        val reqRole = spinnerReqRole.selectedItem.toString()
        val budget = 0 // Default since SeekBar was removed
        val needsPlayer = switchNeedsPlayers.isChecked

        val selectedBallTypeId = rgTeamBallType.checkedRadioButtonId
        val ballType = if (selectedBallTypeId == R.id.rbTeamLeather) "Leather" else "Tennis"

        if (name.isEmpty()) { etTeamName.error = "Team name is required"; etTeamName.requestFocus(); return }
        if (loc.isEmpty()) { etTeamLocation.error = "Location is required"; etTeamLocation.requestFocus(); return }
        if (area.isEmpty()) { etTeamArea.error = "Area is required"; etTeamArea.requestFocus(); return }

        if (selectedDate.isEmpty()) {
            Toast.makeText(this, "Please select a Match Date", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedTime.isEmpty()) {
            Toast.makeText(this, "Please select a Match Time", Toast.LENGTH_SHORT).show()
            return
        }

        if (!com.example.crickzy.utils.DateUtils.isBookingDateLogical(selectedDate)) {
            Toast.makeText(this, "Please select a logical date (recent past or future)", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("CrickzyPrefs", MODE_PRIVATE)
        val userId = prefs.getLong("USER_ID", -1L)

        val newTeam = Team(
            name = name,
            location = loc,
            area = area,
            phone = phone,
            whatsapp = whatsapp,
            requiredRole = reqRole,
            budgetProgress = budget,
            matchDate = selectedDate,
            matchTime = selectedTime,
            needsPlayers = needsPlayer,
            ballType = ballType,
            addedBy = userId
        )

        lifecycleScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { SupabaseHelper.addTeam(newTeam) }
                if (id > -1) {
                    Toast.makeText(this@AddTeamActivity, "Team Registered Successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddTeamActivity, "Error Registering Team. Check backend schema.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddTeamActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
