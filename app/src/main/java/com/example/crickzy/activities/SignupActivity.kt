package com.example.crickzy.activities

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

class SignupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val tilName = findViewById<TextInputLayout>(R.id.tilName)
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val tilEmail = findViewById<TextInputLayout>(R.id.tilSignupEmail)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilSignupPassword)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        btnSignUp.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Reset errors
            tilName.error = null
            tilEmail.error = null
            tilPassword.error = null

            var hasError = false

            if (name.isEmpty()) {
                tilName.error = "Please enter your name"
                hasError = true
            }

            if (email.isEmpty()) {
                tilEmail.error = "Please enter your email"
                hasError = true
            } else if (!email.lowercase().endsWith("@gmail.com") || email.lowercase().indexOf("@gmail.com") == 0) {
                tilEmail.error = "Please enter a valid Gmail address (e.g. user@gmail.com)"
                hasError = true
            }

            if (password.isEmpty()) {
                tilPassword.error = "Please enter a password"
                hasError = true
            } else {
                val hasUppercase = password.any { it.isUpperCase() }
                val hasSpecialChar = password.any { !it.isLetterOrDigit() }
                if (!hasUppercase && !hasSpecialChar) {
                    tilPassword.error = "Password must have at least one uppercase letter and one special character"
                    hasError = true
                } else if (!hasUppercase) {
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
                    val id = withContext(Dispatchers.IO) { SupabaseHelper.addUser(name, email, password) }
                    if (id > -1) {
                        Toast.makeText(this@SignupActivity, "Account Created Successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@SignupActivity, "Registration Failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SignupActivity", "Signup failed: ${e.message}", e)
                    Toast.makeText(this@SignupActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        tvLogin.setOnClickListener {
            finish()
        }
    }
}
