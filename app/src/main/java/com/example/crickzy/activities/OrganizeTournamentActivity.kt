package com.example.crickzy.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.crickzy.R
import com.example.crickzy.database.SupabaseHelper
import com.example.crickzy.models.Tournament
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class OrganizeTournamentActivity : AppCompatActivity() {

    private lateinit var etStartDate: TextInputEditText
    private lateinit var etEndDate: TextInputEditText
    private var startDateMillis: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organize_tournament)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        val etTournamentName = findViewById<TextInputEditText>(R.id.etTournamentName)
        val etTournamentLoc = findViewById<TextInputEditText>(R.id.etTournamentLoc)
        val etGroundName = findViewById<TextInputEditText>(R.id.etGroundName)
        val etOvers = findViewById<TextInputEditText>(R.id.etOvers)
        val etPowerplayOvers = findViewById<TextInputEditText>(R.id.etPowerplayOvers)
        val rgBallType = findViewById<RadioGroup>(R.id.rgBallType)
        etStartDate = findViewById(R.id.etStartDate)
        etEndDate = findViewById(R.id.etEndDate)
        val etEntryFee = findViewById<TextInputEditText>(R.id.etEntryFee)
        val etPrizePool = findViewById<TextInputEditText>(R.id.etPrizePool)
        val etOrganizerPhone = findViewById<TextInputEditText>(R.id.etOrganizerPhone)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitTournament)

        etStartDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val dpd = DatePickerDialog(this, { _, y, m, d ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d)
                startDateMillis = cal.timeInMillis
                etStartDate.setText("$d/${m + 1}/$y")
                etEndDate.setText("")
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            dpd.datePicker.minDate = System.currentTimeMillis() - 1000
            dpd.show()
        }

        etEndDate.setOnClickListener {
            if (startDateMillis == 0L) {
                Toast.makeText(this, "Please select Start Date first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = startDateMillis
            val dpd = DatePickerDialog(this, { _, y, m, d ->
                etEndDate.setText("$d/${m + 1}/$y")
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            dpd.datePicker.minDate = startDateMillis
            dpd.show()
        }

        btnSubmit.setOnClickListener {
            val name = etTournamentName.text.toString().trim()
            val loc = etTournamentLoc.text.toString().trim()
            val groundName = etGroundName.text.toString().trim()
            val oversStr = etOvers.text.toString().trim()
            val powerplayStr = etPowerplayOvers.text.toString().trim()
            val start = etStartDate.text.toString().trim()
            val end = etEndDate.text.toString().trim()
            val feeStr = etEntryFee.text.toString().trim()
            val prize = etPrizePool.text.toString().trim()
            val phone = etOrganizerPhone.text.toString().trim()

            val selectedBallTypeId = rgBallType.checkedRadioButtonId
            val ballType = if (selectedBallTypeId != -1) {
                findViewById<RadioButton>(selectedBallTypeId).text.toString()
            } else "Tennis"

            if (name.isEmpty()) { etTournamentName.error = "Tournament name is required"; etTournamentName.requestFocus(); return@setOnClickListener }
            if (loc.isEmpty()) { etTournamentLoc.error = "Location is required"; etTournamentLoc.requestFocus(); return@setOnClickListener }
            if (groundName.isEmpty()) { etGroundName.error = "Ground name is required"; etGroundName.requestFocus(); return@setOnClickListener }
            if (oversStr.isEmpty()) { etOvers.error = "Overs are required"; etOvers.requestFocus(); return@setOnClickListener }
            if (start.isEmpty()) { Toast.makeText(this, "Start Date is required", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (end.isEmpty()) { Toast.makeText(this, "End Date is required", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (feeStr.isEmpty()) { etEntryFee.error = "Entry fee is required (can be 0)"; etEntryFee.requestFocus(); return@setOnClickListener }
            if (prize.isEmpty()) { etPrizePool.error = "Prize pool info is required"; etPrizePool.requestFocus(); return@setOnClickListener }
            if (phone.length != 10) { etOrganizerPhone.error = "Phone number must be exactly 10 digits"; etOrganizerPhone.requestFocus(); return@setOnClickListener }
            if (phone.any { !it.isDigit() }) { etOrganizerPhone.error = "Phone number must contain only digits"; etOrganizerPhone.requestFocus(); return@setOnClickListener }

            val overs = oversStr.toIntOrNull() ?: 20
            val powerplayOvers = powerplayStr.toIntOrNull() ?: 6
            val fee = feeStr.toDoubleOrNull() ?: 0.0

            if (!com.example.crickzy.utils.DateUtils.isFutureDate(start)) {
                Toast.makeText(this, "Tournament Start Date must be today or in the future", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (powerplayOvers >= overs) {
                etPowerplayOvers.error = "Powerplay must be strictly less than total overs"
                etPowerplayOvers.requestFocus()
                return@setOnClickListener
            }

            if (start.isNotEmpty() && end.isNotEmpty() && !com.example.crickzy.utils.DateUtils.isDateRangeValid(start, end)) {
                Toast.makeText(this, "End Date cannot be before Start Date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (prize.any { !it.isDigit() && it != '.' }) {
                etPrizePool.error = "Prize pool must be a number"; etPrizePool.requestFocus(); return@setOnClickListener
            }

            val prefs = getSharedPreferences("CrickzyPrefs", MODE_PRIVATE)
            val loggedInEmail = prefs.getString("loggedInEmail", "") ?: ""

            val selectedFormatId = findViewById<RadioGroup>(R.id.rgFormat).checkedRadioButtonId
            val format = when(selectedFormatId) {
                R.id.rbLeague -> "League"
                R.id.rbODI -> "ODI"
                else -> "Knockout"
            }

            val tournament = Tournament(
                name = name, location = loc, startDate = start, endDate = end,
                entryFee = fee, prizePool = if (prize.isEmpty()) "Not specified" else prize,
                organizerPhone = if (phone.isEmpty()) "Not specified" else phone,
                overs = overs, groundName = groundName, ballType = ballType, powerplayOvers = powerplayOvers,
                format = format,
                organizerEmail = loggedInEmail
            )

            lifecycleScope.launch {
                try {
                    val id = withContext(Dispatchers.IO) { SupabaseHelper.addTournament(tournament) }
                    if (id > -1) {
                        Toast.makeText(this@OrganizeTournamentActivity, "Tournament Launched Successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@OrganizeTournamentActivity, "Failed to launch tournament", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@OrganizeTournamentActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
