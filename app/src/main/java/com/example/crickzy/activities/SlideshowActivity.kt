package com.example.crickzy.activities

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.crickzy.R
import com.example.crickzy.adapters.SlideItem
import com.example.crickzy.adapters.SlideshowAdapter
import com.google.android.material.button.MaterialButton

class SlideshowActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var layoutIndicators: LinearLayout
    private lateinit var btnNext: MaterialButton
    private lateinit var tvSkip: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slideshow)

        viewPager = findViewById(R.id.viewPager)
        layoutIndicators = findViewById(R.id.layoutIndicators)
        btnNext = findViewById(R.id.btnNext)
        tvSkip = findViewById(R.id.tvSkip)

        val slides = listOf(
            SlideItem(
                title = "Live Scoring Engine",
                description = "Track ball-by-ball runs, wickets, and extras. Easily undo scoring mistakes with instant state recovery.",
                iconResId = R.drawable.ic_role_batsman,
                startColorRes = R.color.gradient_start,
                endColorRes = R.color.gradient_end
            ),
            SlideItem(
                title = "Smart Tournaments",
                description = "Set up knockout brackets and round-robin leagues. Automatically seed matches, advance winners, and track point tables.",
                iconResId = R.drawable.ic_cricket_logo,
                startColorRes = R.color.score_six,
                endColorRes = R.color.primary_light
            ),
            SlideItem(
                title = "Scan & Join via QR",
                description = "Register your squad and generate unique deep-linked QR codes. Allow players to join your team by scanning in seconds.",
                iconResId = android.R.drawable.ic_menu_camera,
                startColorRes = R.color.secondary,
                endColorRes = R.color.secondary_variant
            ),
            SlideItem(
                title = "Turf Slot Booking",
                description = "Find nearby turfs and reserve your match slots. Receive automated SMS reminders with date, time, and pricing.",
                iconResId = android.R.drawable.ic_menu_myplaces,
                startColorRes = R.color.score_one,
                endColorRes = R.color.primary_light
            )
        )

        viewPager.adapter = SlideshowAdapter(slides)

        // Sleek scale and fade page transition animation
        viewPager.setPageTransformer { page, position ->
            val absPosition = Math.abs(position)
            page.alpha = 1f - absPosition
            // Parallax and scale down effect
            page.scaleX = 0.85f + (1f - absPosition) * 0.15f
            page.scaleY = 0.85f + (1f - absPosition) * 0.15f
            page.translationX = position * -page.width * 0.2f
        }

        setupIndicators(slides.size)
        updateIndicators(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)

                if (position == slides.size - 1) {
                    btnNext.text = "Get Started"
                    tvSkip.visibility = View.GONE
                } else {
                    btnNext.text = "Next"
                    tvSkip.visibility = View.VISIBLE
                }
            }
        })

        btnNext.setOnClickListener {
            val currentPos = viewPager.currentItem
            if (currentPos < slides.size - 1) {
                viewPager.currentItem = currentPos + 1
            } else {
                finishOnboarding()
            }
        }

        tvSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun setupIndicators(count: Int) {
        val indicators = arrayOfNulls<ImageView>(count)
        val density = resources.displayMetrics.density
        val size = (8 * density).toInt()
        val margin = (6 * density).toInt()

        layoutIndicators.removeAllViews()

        for (i in 0 until count) {
            indicators[i] = ImageView(this)
            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(margin, 0, margin, 0)
            indicators[i]?.layoutParams = params
            layoutIndicators.addView(indicators[i])
        }
    }

    private fun updateIndicators(position: Int) {
        val childCount = layoutIndicators.childCount
        val density = resources.displayMetrics.density
        val activeWidth = (20 * density).toInt()
        val inactiveSize = (8 * density).toInt()

        for (i in 0 until childCount) {
            val imageView = layoutIndicators.getChildAt(i) as ImageView
            
            // Programmatically draw dot drawable: rounded rectangle
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = 10 * density

            if (i == position) {
                // Active indicator is longer and colored primary
                shape.setColor(ContextCompat.getColor(this, R.color.primary))
                val params = LinearLayout.LayoutParams(activeWidth, inactiveSize)
                params.setMargins((6 * density).toInt(), 0, (6 * density).toInt(), 0)
                imageView.layoutParams = params
            } else {
                // Inactive indicator is smaller and grey
                shape.setColor(ContextCompat.getColor(this, R.color.nav_inactive))
                val params = LinearLayout.LayoutParams(inactiveSize, inactiveSize)
                params.setMargins((6 * density).toInt(), 0, (6 * density).toInt(), 0)
                imageView.layoutParams = params
            }
            
            imageView.setImageDrawable(shape)
        }
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences("CrickzyPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isFirstRun", false).apply()

        val loggedInEmail = prefs.getString("loggedInEmail", null)
        if (loggedInEmail != null) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}
