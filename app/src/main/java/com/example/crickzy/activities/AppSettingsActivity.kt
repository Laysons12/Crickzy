package com.example.crickzy.activities

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.crickzy.R

class AppSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.background, null))
        }

        val toolbar = Toolbar(this).apply {
            title = "App Settings"
            setTitleTextColor(resources.getColor(R.color.white, null))
            setBackgroundColor(resources.getColor(R.color.primary, null))
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { onBackPressed() }
        }

        val tvPlaceholder = TextView(this).apply {
            text = "App Settings will be available here soon!"
            textSize = 18f
            setPadding(48, 48, 48, 48)
            setTextColor(resources.getColor(R.color.text_primary, null))
        }

        layout.addView(toolbar)
        layout.addView(tvPlaceholder)
        setContentView(layout)
    }
}
