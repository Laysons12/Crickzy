package com.example.crickzy.activities

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.crickzy.R
import com.example.crickzy.models.BatsmanInnings
import com.example.crickzy.models.BowlerInnings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ScorecardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scorecard)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        val teamName = intent.getStringExtra("teamName") ?: "Team"
        val totalRuns = intent.getIntExtra("totalRuns", 0)
        val totalWickets = intent.getIntExtra("totalWickets", 0)
        val totalBalls = intent.getIntExtra("totalBalls", 0)
        val extras = intent.getIntExtra("extras", 0)
        val batsmenJson = intent.getStringExtra("batsmenJson")
        val bowlersJson = intent.getStringExtra("bowlersJson")

        val gson = Gson()
        val batsmenType = object : TypeToken<List<BatsmanInnings>>() {}.type
        val bowlersType = object : TypeToken<List<BowlerInnings>>() {}.type

        val batsmen: List<BatsmanInnings> = gson.fromJson(batsmenJson, batsmenType) ?: emptyList()
        val bowlers: List<BowlerInnings> = gson.fromJson(bowlersJson, bowlersType) ?: emptyList()

        findViewById<TextView>(R.id.tvTeamName).text = teamName
        findViewById<TextView>(R.id.tvTotalScore).text = "$totalRuns/$totalWickets"
        val overs = totalBalls / 6
        val balls = totalBalls % 6
        findViewById<TextView>(R.id.tvTotalOvers).text = "($overs.$balls Overs)"
        findViewById<TextView>(R.id.tvExtrasTotal).text = "Extras: $extras"
        
        val rr = if (totalBalls > 0) String.format("%.2f", (totalRuns * 6.0 / totalBalls)) else "0.00"
        findViewById<TextView>(R.id.tvRRSummary).text = "RR: $rr"

        val llBattingTable = findViewById<LinearLayout>(R.id.llBattingTable)
        val llBowlingTable = findViewById<LinearLayout>(R.id.llBowlingTable)
        val tvBattingFooter = findViewById<TextView>(R.id.tvBattingSummaryFooter)
        val tvBowlingFooter = findViewById<TextView>(R.id.tvBowlingSummaryFooter)

        setupBattingTable(llBattingTable, tvBattingFooter, batsmen)
        setupBowlingTable(llBowlingTable, tvBowlingFooter, bowlers)
    }

    private fun setupBattingTable(parent: LinearLayout, footer: TextView, batsmen: List<BatsmanInnings>) {
        parent.removeAllViews()
        // Header
        val header = createRow(listOf("Batter", "R", "B", "4s", "6s", "SR"), true)
        parent.addView(header)
        addDivider(parent)

        var battersParticipated = 0
        for (b in batsmen) {
            if (b.runs == 0 && b.ballsFaced == 0 && !b.isOut && !b.isOnStrike) continue
            battersParticipated++
            
            val sr = if (b.ballsFaced > 0) String.format("%.2f", (b.runs * 100.0 / b.ballsFaced)) else "0.00"
            val statusColor = if (b.isOnStrike) "#80CBC4" else (if (b.isOut) "#9E9E9E" else "#FFFFFF")
            
            val row = createRow(listOf(
                b.name + (if (b.isOnStrike) "*" else "") + (if (b.isOut) "" else " (not out)"),
                b.runs.toString(),
                b.ballsFaced.toString(),
                b.fours.toString(),
                b.sixes.toString(),
                sr
            ), false, statusColor)
            parent.addView(row)
            
            if (b.dismissalInfo.isNotEmpty()) {
                val info = TextView(this).apply {
                    text = b.dismissalInfo
                    textSize = 10f
                    setTextColor(Color.parseColor("#9E9E9E"))
                    setPadding(12, 0, 0, 4)
                }
                parent.addView(info)
            }
            addDivider(parent)
        }
        footer.text = "Total Batters: $battersParticipated"
    }

    private fun setupBowlingTable(parent: LinearLayout, footer: TextView, bowlers: List<BowlerInnings>) {
        parent.removeAllViews()
        // Header
        val header = createRow(listOf("Bowler", "O", "M", "R", "W", "Econ"), true)
        parent.addView(header)
        addDivider(parent)

        for (b in bowlers) {
            val oversDisplay = "${b.ballsBowled / 6}.${b.ballsBowled % 6}"
            val econ = if (b.ballsBowled > 0) String.format("%.2f", (b.runsConceded * 6.0 / b.ballsBowled)) else "0.00"
            val row = createRow(listOf(
                b.name,
                oversDisplay,
                b.maidens.toString(),
                b.runsConceded.toString(),
                b.wickets.toString(),
                econ
            ), false)
            parent.addView(row)
            addDivider(parent)
        }
        footer.text = "Total Bowlers: ${bowlers.size}"
    }

    private fun createRow(cells: List<String>, isHeader: Boolean, textColorStr: String = "#FFFFFF"): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
            gravity = Gravity.CENTER_VERTICAL
        }

        val weights = listOf(3f, 1f, 1f, 1f, 1f, 1.4f)
        for (i in cells.indices) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weights[i])
                text = cells[i]
                textSize = if (isHeader) 11f else 12f
                setTextColor(if (isHeader) Color.parseColor("#90A4AE") else Color.parseColor(textColorStr))
                if (isHeader) setTypeface(null, Typeface.BOLD)
                if (i > 0) gravity = Gravity.CENTER
            }
            row.addView(tv)
        }
        return row
    }

    private fun addDivider(parent: LinearLayout) {
        val v = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#1F3355"))
        }
        parent.addView(v)
    }

    // Extension for sp - REMOVED because it was causing double-scaling with setTextSize
    // private val Int.sp: Float get() = this * resources.displayMetrics.scaledDensity
}
