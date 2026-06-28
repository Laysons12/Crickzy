package com.example.crickzy.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.crickzy.R

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved dark mode preference before setting content view
        val prefs = getSharedPreferences("crickzy_prefs", 0)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val prefs = getSharedPreferences("CrickzyPrefs", MODE_PRIVATE)
            val isFirstRun = prefs.getBoolean("isFirstRun", true)
            val loggedInEmail = prefs.getString("loggedInEmail", null)
            if (isFirstRun) {
                startActivity(Intent(this, SlideshowActivity::class.java))
            } else if (loggedInEmail != null) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 2000)
    }
}
