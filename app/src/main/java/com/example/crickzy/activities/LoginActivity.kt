package com.example.crickzy.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.crickzy.R
import com.example.crickzy.database.SupabaseHelper
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)

        btnLogin.setOnClickListener {
            val email = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Reset errors
            tilEmail.error = null
            tilPassword.error = null

            var hasError = false

            // Email validation: must end with @gmail.com
            if (email.isEmpty()) {
                tilEmail.error = "Please enter your email"
                hasError = true
            } else if (!email.lowercase().endsWith("@gmail.com") || email.lowercase().indexOf("@gmail.com") == 0) {
                tilEmail.error = "Please enter a valid Gmail address (e.g. user@gmail.com)"
                hasError = true
            }

            // Password validation: at least 1 uppercase and 1 special character
            if (password.isEmpty()) {
                tilPassword.error = "Please enter your password"
                hasError = true
            } else {
                val hasUppercase = password.any { it.isUpperCase() }
                val hasSpecialChar = password.any { !it.isLetterOrDigit() }
                if (!hasUppercase) {
                    tilPassword.error = "Password must contain at least one uppercase letter"
                    hasError = true
                } else if (!hasSpecialChar) {
                    tilPassword.error = "Password must contain at least one special character (e.g. @, #, \$, !)"
                    hasError = true
                }
            }

            if (hasError) return@setOnClickListener

            lifecycleScope.launch {
                try {
                    val isValid = withContext(Dispatchers.IO) { SupabaseHelper.checkUser(email, password) }
                    if (isValid) {
                        val name = withContext(Dispatchers.IO) { SupabaseHelper.getUserNameByEmail(email) }
                        val userId = withContext(Dispatchers.IO) { SupabaseHelper.getUserIdByEmail(email) }
                        val prefs = getSharedPreferences("CrickzyPrefs", MODE_PRIVATE)
                        prefs.edit()
                            .putString("loggedInEmail", email)
                            .putString("loggedInName", name)
                            .putLong("USER_ID", userId)
                            .apply()

                        Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "Invalid Email or Password", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LoginActivity", "Login failed: ${e.message}", e)
                    Toast.makeText(this@LoginActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        tvForgotPassword.setOnClickListener {
            val emailInput = com.google.android.material.textfield.TextInputEditText(this)
            emailInput.hint = "Enter your registered email"
            val padding = (16 * resources.displayMetrics.density).toInt()
            
            val container = android.widget.FrameLayout(this)
            container.setPadding(padding, padding, padding, padding)
            container.addView(emailInput)

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Forgot Password")
                .setMessage("Please enter your email to retrieve your password.")
                .setView(container)
                .setPositiveButton("Retrieve") { _, _ ->
                    val emailText = emailInput.text.toString().trim()
                    if (emailText.isNotEmpty()) {
                        lifecycleScope.launch {
                            try {
                                val pwd = withContext(Dispatchers.IO) { SupabaseHelper.getPasswordByEmail(emailText) }
                                if (pwd.isNotEmpty()) {
                                    androidx.appcompat.app.AlertDialog.Builder(this@LoginActivity)
                                        .setTitle("Password Retrieved")
                                        .setMessage("Your password is: $pwd\n\n(Note: Passwords should not be shown like this in production)")
                                        .setPositiveButton("OK", null)
                                        .show()
                                } else {
                                    Toast.makeText(this@LoginActivity, "Email not found", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@LoginActivity, "Network error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Please enter an email", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
