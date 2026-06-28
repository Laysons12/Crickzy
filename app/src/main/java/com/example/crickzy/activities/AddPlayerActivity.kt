package com.example.crickzy.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.crickzy.R
import com.example.crickzy.database.SupabaseHelper
import com.example.crickzy.models.Player
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class AddPlayerActivity : AppCompatActivity() {

    private lateinit var etName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etWhatsapp: TextInputEditText
    private lateinit var etArea: TextInputEditText
    private lateinit var spinnerRole: Spinner
    private lateinit var rgMatchType: RadioGroup
    private lateinit var rgBallType: RadioGroup
    private lateinit var cbWicketKeeper: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var switchAvailability: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var sbSkill: SeekBar
    private lateinit var rbExperience: RatingBar
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var tvDateTimePreview: TextView
    private lateinit var btnSaveProfile: Button
    private lateinit var btnSelectImage: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var ivProfileImage: ImageView

    private var selectedDate = ""
    private var selectedTime = ""
    private var selectedImageUri = "default"
    private var isUpdating = false
    private var loggedInEmail = ""

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri.toString()
            ivProfileImage.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_player)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        initViews()
        setupListeners()
        loadProfile()
    }

    private fun loadProfile() {
        val prefs = getSharedPreferences("CrickzyPrefs", MODE_PRIVATE)
        val nameToSearch = prefs.getString("loggedInName", "") ?: ""
        
        if (nameToSearch.isEmpty()) return

        lifecycleScope.launch {
            try {
                val player = withContext(Dispatchers.IO) { SupabaseHelper.getPlayerByName(nameToSearch) }
                if (player != null) {
                    isUpdating = true
                    etName.setText(player.name)
                    etPhone.setText(player.phone)
                    etWhatsapp.setText(player.whatsapp)
                    etArea.setText(player.area)
                    
                    val roles = arrayOf("Batsman", "Bowler", "All-Rounder", "Wicket-Keeper")
                    val roleIndex = roles.indexOf(player.role)
                    if (roleIndex != -1) spinnerRole.setSelection(roleIndex)

                    if (player.matchType == "T20") rgMatchType.check(R.id.rbT20)
                    else rgMatchType.check(R.id.rbODI)

                    if (player.ballType == "Leather") rgBallType.check(R.id.rbLeather)
                    else rgBallType.check(R.id.rbTennis)

                    cbWicketKeeper.isChecked = player.isWicketKeeper
                    switchAvailability.isChecked = player.isAvailable
                    sbSkill.progress = player.skillRating
                    rbExperience.rating = player.expLevel
                    
                    selectedDate = player.availabilityDate
                    selectedTime = player.matchTime
                    updateDateTimePreview()
                    
                    btnSaveProfile.text = "Update Profile"
                    findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).title = "Edit Profile"
                }
            } catch (e: Exception) {
                android.util.Log.e("AddPlayerActivity", "Load profile failed: ${e.message}", e)
            }
        }
    }

    private fun initViews() {
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etWhatsapp = findViewById(R.id.etWhatsapp)
        etArea = findViewById(R.id.etArea)
        spinnerRole = findViewById(R.id.spinnerRole)
        rgMatchType = findViewById(R.id.rgMatchType)
        rgBallType = findViewById(R.id.rgBallType)
        cbWicketKeeper = findViewById(R.id.cbWicketKeeper)
        switchAvailability = findViewById(R.id.switchAvailability)
        sbSkill = findViewById(R.id.sbSkill)
        rbExperience = findViewById(R.id.rbExperience)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSelectTime = findViewById(R.id.btnSelectTime)
        tvDateTimePreview = findViewById(R.id.tvDateTimePreview)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        
        btnSelectImage = findViewById(R.id.btnSelectImage)
        ivProfileImage = findViewById(R.id.ivProfileImage)

        val roles = arrayOf("Batsman", "Bowler", "All-Rounder", "Wicket-Keeper")
        val adapter = ArrayAdapter(this, R.layout.item_spinner, roles)
        adapter.setDropDownViewResource(R.layout.item_spinner)
        spinnerRole.adapter = adapter
    }

    private fun setupListeners() {
        btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val dpd = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                selectedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                updateDateTimePreview()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            dpd.datePicker.minDate = calendar.timeInMillis
            dpd.show()
        }

        btnSelectTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                selectedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                updateDateTimePreview()
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        btnSaveProfile.setOnClickListener {
            savePlayerProfile()
        }

        btnSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun updateDateTimePreview() {
        tvDateTimePreview.text = "Selected: Date: $selectedDate | Time: $selectedTime"
    }

    private fun savePlayerProfile() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val whatsapp = etWhatsapp.text.toString().trim()
        val area = etArea.text.toString().trim()
        val role = spinnerRole.selectedItem.toString()

        val selectedMatchTypeId = rgMatchType.checkedRadioButtonId
        val matchType = if (selectedMatchTypeId != -1) {
            findViewById<RadioButton>(selectedMatchTypeId).text.toString()
        } else {
            "T20"
        }

        val selectedBallTypeId = rgBallType.checkedRadioButtonId
        val ballType = if (selectedBallTypeId == R.id.rbLeather) "Leather" else "Tennis"

        val isWk = cbWicketKeeper.isChecked
        val isAvailable = switchAvailability.isChecked
        val skillRating = sbSkill.progress
        val experience = rbExperience.rating

        if (name.isEmpty()) { etName.error = "Name is required"; etName.requestFocus(); return }
        if (phone.length != 10) { etPhone.error = "Phone number must be exactly 10 digits"; etPhone.requestFocus(); return }
        if (phone.any { !it.isDigit() }) { etPhone.error = "Phone number must contain only digits"; etPhone.requestFocus(); return }
        if (area.isEmpty()) { etArea.error = "Area is required"; etArea.requestFocus(); return }

        if (selectedDate.isEmpty()) {
            Toast.makeText(this, "Please select an Availability Date", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedTime.isEmpty()) {
            Toast.makeText(this, "Please select an Availability Time", Toast.LENGTH_SHORT).show()
            return
        }

        if (!com.example.crickzy.utils.DateUtils.isBookingDateLogical(selectedDate)) {
            Toast.makeText(this, "Please select a logical date (recent past or future)", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("CrickzyPrefs", MODE_PRIVATE)
        val userId = prefs.getLong("USER_ID", -1L)

        val newPlayer = Player(
            name = name, phone = phone, whatsapp = whatsapp, role = role, matchType = matchType,
            isWicketKeeper = isWk, isAvailable = isAvailable, skillRating = skillRating,
            expLevel = experience, availabilityDate = selectedDate, matchTime = selectedTime,
            profileImageUri = selectedImageUri, ballType = ballType, area = area, addedBy = userId
        )

        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    if (isUpdating) {
                        SupabaseHelper.updatePlayer(newPlayer)
                    } else {
                        SupabaseHelper.addPlayer(newPlayer) > -1
                    }
                }
                if (success) {
                    // Update local storage for immediate UI feedback in Settings
                    getSharedPreferences("CrickzyPrefs", MODE_PRIVATE).edit()
                        .putString("loggedInName", name)
                        .apply()
                        
                    Toast.makeText(this@AddPlayerActivity, "Player Profile Saved Successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddPlayerActivity, "Error Saving Profile: ${SupabaseHelper.lastError}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("AddPlayerActivity", "Save failed: ${e.message}", e)
                Toast.makeText(this@AddPlayerActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
