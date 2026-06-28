package com.example.crickzy.activities

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.crickzy.R
import com.example.crickzy.database.SupabaseHelper
import com.example.crickzy.models.Turf
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddTurfActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_turf)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        val etName = findViewById<TextInputEditText>(R.id.etTurfName)
        val etLocation = findViewById<TextInputEditText>(R.id.etTurfLocation)
        val etPrice = findViewById<TextInputEditText>(R.id.etTurfPrice)
        val btnAdd = findViewById<Button>(R.id.btnSubmitTurf)

        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            val location = etLocation.text.toString().trim()
            val priceStr = etPrice.text.toString().trim()

            if (name.isEmpty() || location.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Please fill all details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val price = priceStr.toDoubleOrNull() ?: 0.0

            val input = android.widget.EditText(this@AddTurfActivity).apply {
                hint = "https://example.com/image.jpg"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            }

            androidx.appcompat.app.AlertDialog.Builder(this@AddTurfActivity)
                .setTitle("Add a Photo?")
                .setMessage("Paste a web URL for the turf's image, or skip to use the default photo.")
                .setView(input)
                .setPositiveButton("Save with Photo") { _, _ ->
                    val url = input.text.toString().trim()
                    saveTurf(name, location, price, url.ifEmpty { "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e" })
                }
                .setNegativeButton("Skip") { _, _ ->
                    saveTurf(name, location, price, "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e")
                }
                .show()
        }
    }

    private fun saveTurf(name: String, location: String, price: Double, imageUrl: String) {
        val prefs = getSharedPreferences("CrickzyPrefs", android.content.Context.MODE_PRIVATE)
        val userId = prefs.getLong("USER_ID", -1L)

        lifecycleScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { 
                    SupabaseHelper.addTurf(Turf(name = name, location = location, pricePerHour = price, imageUrl = imageUrl, addedBy = userId)) 
                }
                if (id > -1) {
                    Toast.makeText(this@AddTurfActivity, "Turf successfully added!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddTurfActivity, "Failed to add turf", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddTurfActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
