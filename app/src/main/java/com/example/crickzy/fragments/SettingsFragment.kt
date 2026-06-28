package com.example.crickzy.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.crickzy.R
import com.example.crickzy.activities.LoginActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        val switchNotifications = view.findViewById<SwitchMaterial>(R.id.switchNotifications)
        val switchDarkMode = view.findViewById<SwitchMaterial>(R.id.switchDarkMode)
        val btnLogout = view.findViewById<Button>(R.id.btnLogout)
        val tvProfileName = view.findViewById<android.widget.TextView>(R.id.tvProfileName)
        val tvProfileEmail = view.findViewById<android.widget.TextView>(R.id.tvProfileEmail)
        
        val prefs_crickzy = requireContext().getSharedPreferences("CrickzyPrefs", android.content.Context.MODE_PRIVATE)
        val loggedInEmail = prefs_crickzy.getString("loggedInEmail", "user@gmail.com")
        val loggedInName = prefs_crickzy.getString("loggedInName", "Player")
        
        tvProfileEmail.text = loggedInEmail
        tvProfileName.text = loggedInName
        
        // Make the whole profile row clickable
        val profileClickArea = tvProfileEmail.parent as View
        profileClickArea.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.crickzy.activities.AddPlayerActivity::class.java))
        }

        val menuAppSetting = view.findViewById<View>(R.id.menuAppSetting)
        menuAppSetting.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.crickzy.activities.AppSettingsActivity::class.java))
        }

        val menuDailyPrefs = view.findViewById<View>(R.id.menuDailyPrefs)
        menuDailyPrefs?.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.crickzy.activities.DailyPrefsActivity::class.java))
        }

        // Load saved dark mode preference
        val prefs = requireContext().getSharedPreferences("crickzy_prefs", 0)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        switchDarkMode.isChecked = isDarkMode

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(requireContext(), "Notifications $status", Toast.LENGTH_SHORT).show()
        }

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            // Save preference
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        btnLogout.setOnClickListener {
            // Logout logic
            val intent = Intent(activity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        // Refresh profile info in case it was updated in AddPlayerActivity
        val view = view ?: return
        val tvProfileName = view.findViewById<android.widget.TextView>(R.id.tvProfileName)
        val tvProfileEmail = view.findViewById<android.widget.TextView>(R.id.tvProfileEmail)
        
        val prefs = requireContext().getSharedPreferences("CrickzyPrefs", android.content.Context.MODE_PRIVATE)
        tvProfileName.text = prefs.getString("loggedInName", "Player")
        tvProfileEmail.text = prefs.getString("loggedInEmail", "user@gmail.com")
    }
}
