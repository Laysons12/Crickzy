package com.example.crickzy.activities

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.crickzy.R
import com.example.crickzy.database.SupabaseHelper
import com.example.crickzy.models.BatsmanInnings
import com.example.crickzy.models.BowlerInnings
import com.example.crickzy.models.Match
import com.example.crickzy.models.ScoringSnapshot
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.crickzy.utils.safeSupabaseCall
import com.google.gson.Gson
import android.content.Intent
import java.util.ArrayList
import android.util.Log
import com.example.crickzy.models.Player

class ScoreActivity : AppCompatActivity() {

    private var currentMatch: Match? = null
    private var isTeam1Batting = true
    private val gson = Gson()

    // Undo
    private val undoStack = mutableListOf<ScoringSnapshot>()
    private val currentOverBalls = mutableListOf<String>()

    // Batsman tracking
    private var striker: BatsmanInnings? = null
    private var nonStriker: BatsmanInnings? = null
    private val allBatsmenInnings1 = mutableListOf<BatsmanInnings>()
    private val allBatsmenInnings2 = mutableListOf<BatsmanInnings>()

    // Bowler tracking
    private var currentBowler: BowlerInnings? = null
    private val allBowlersInnings1 = mutableListOf<BowlerInnings>()
    private val allBowlersInnings2 = mutableListOf<BowlerInnings>()

    // Tournament match data
    private var tournamentId: Long = -1
    private var fixtureId: Long = -1
    private var existingMatchId: Long = -1
    private var totalOvers: Int = 20

    // Player lists from tournament team registration
    private var team1Players = arrayListOf<String>()
    private var team2Players = arrayListOf<String>()

    // Track which players are already batting (used) in this innings
    private val usedBatsmen1 = mutableListOf<String>()
    private val usedBatsmen2 = mutableListOf<String>()

    // Toss info
    private var team1Name = ""
    private var team2Name = ""

    private var lastBowlerName: String? = null

    // Views
    private lateinit var tvCurrentBatting: TextView
    private lateinit var tvLiveScore: TextView
    private lateinit var tvOversDisplay: TextView
    private lateinit var tvRunRate: TextView
    private lateinit var tvExtras: TextView
    private lateinit var tvTarget: TextView
    private lateinit var llThisOver: LinearLayout
    private lateinit var tvStrikerName: TextView
    private lateinit var tvStrikerRuns: TextView
    private lateinit var tvStrikerBalls: TextView
    private lateinit var tvStrikerSR: TextView
    private lateinit var tvNonStrikerName: TextView
    private lateinit var tvNonStrikerRuns: TextView
    private lateinit var tvNonStrikerBalls: TextView
    private lateinit var tvNonStrikerSR: TextView
    private lateinit var tvBowlerName: TextView
    private lateinit var tvBowlerOvers: TextView
    private lateinit var tvBowlerRuns: TextView
    private lateinit var tvBowlerWickets: TextView
    private lateinit var llScoreControls: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_score)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        tournamentId = intent.getLongExtra("tournamentId", -1)
        fixtureId = intent.getLongExtra("fixtureId", -1)
        existingMatchId = intent.getLongExtra("matchId", -1)
        totalOvers = intent.getIntExtra("totalOvers", 20)
        team1Name = intent.getStringExtra("team1Name") ?: ""
        team2Name = intent.getStringExtra("team2Name") ?: ""
        team1Players = intent.getStringArrayListExtra("team1Players")?.let { deduplicateNames(it) } ?: arrayListOf()
        team2Players = intent.getStringArrayListExtra("team2Players")?.let { deduplicateNames(it) } ?: arrayListOf()

        val isNewMatch = intent.getBooleanExtra("isNewMatch", false)
        if (isNewMatch && existingMatchId > 0) {
            try {
                val prefs = getSharedPreferences("match_state_$existingMatchId", MODE_PRIVATE)
                prefs.edit().clear().apply()
                Log.d("ScoreActivity", "Cleared local cache because this is a brand new match (ID: $existingMatchId)")
            } catch (e: Exception) {
                Log.e("ScoreActivity", "Failed to clear new match cache: ${e.message}")
            }
        }

        val etTeam1 = findViewById<TextInputEditText>(R.id.etTeam1)
        val etTeam2 = findViewById<TextInputEditText>(R.id.etTeam2)
        val btnInitMatch = findViewById<Button>(R.id.btnInitMatch)
        val llTeamInput = findViewById<LinearLayout>(R.id.llTeamInput)

        llScoreControls = findViewById(R.id.llScoreControls)
        tvCurrentBatting = findViewById(R.id.tvCurrentBatting)
        tvLiveScore = findViewById(R.id.tvLiveScore)
        tvOversDisplay = findViewById(R.id.tvOversDisplay)
        tvRunRate = findViewById(R.id.tvRunRate)
        tvExtras = findViewById(R.id.tvExtras)
        tvTarget = findViewById(R.id.tvTarget)
        llThisOver = findViewById(R.id.llThisOver)

        tvStrikerName = findViewById(R.id.tvStrikerName)
        tvStrikerRuns = findViewById(R.id.tvStrikerRuns)
        tvStrikerBalls = findViewById(R.id.tvStrikerBalls)
        tvStrikerSR = findViewById(R.id.tvStrikerSR)
        tvNonStrikerName = findViewById(R.id.tvNonStrikerName)
        tvNonStrikerRuns = findViewById(R.id.tvNonStrikerRuns)
        tvNonStrikerBalls = findViewById(R.id.tvNonStrikerBalls)
        tvNonStrikerSR = findViewById(R.id.tvNonStrikerSR)

        tvBowlerName = findViewById(R.id.tvBowlerName)
        tvBowlerOvers = findViewById(R.id.tvBowlerOvers)
        tvBowlerRuns = findViewById(R.id.tvBowlerRuns)
        tvBowlerWickets = findViewById(R.id.tvBowlerWickets)

        // Wire buttons
        findViewById<Button>(R.id.btnDot).setOnClickListener { addLegalDelivery(0, "•") }
        findViewById<Button>(R.id.btnAdd1).setOnClickListener { addLegalDelivery(1, "1") }
        findViewById<Button>(R.id.btnAdd2).setOnClickListener { addLegalDelivery(2, "2") }
        findViewById<Button>(R.id.btnAdd3).setOnClickListener { addLegalDelivery(3, "3") }
        findViewById<Button>(R.id.btnAdd4).setOnClickListener { addLegalDelivery(4, "4") }
        findViewById<Button>(R.id.btnAdd5).setOnClickListener { addLegalDelivery(5, "5") }
        findViewById<Button>(R.id.btnAdd6).setOnClickListener { addLegalDelivery(6, "6") }
        findViewById<Button>(R.id.btnWide).setOnClickListener { handleExtra("Wd") }
        findViewById<Button>(R.id.btnNoBall).setOnClickListener { handleExtra("Nb") }
        findViewById<Button>(R.id.btnOut).setOnClickListener { showDismissalDialog() }
        findViewById<Button>(R.id.btnFinishInnings).setOnClickListener {
            if (isTeam1Batting) { handleInningsChange(); updateScoreDisplay() }
            else handleMatchFinish()
        }
        findViewById<Button>(R.id.btnUndo).setOnClickListener { performUndo() }
        findViewById<Button>(R.id.btnViewScorecard).setOnClickListener { showFullScorecard() }

        // ======== LAUNCH FLOW ========
        if (existingMatchId > 0 || (team1Name.isNotEmpty() && team2Name.isNotEmpty())) {
            etTeam1.setText(team1Name); etTeam2.setText(team2Name)
            etTeam1.isEnabled = false; etTeam2.isEnabled = false
            btnInitMatch.visibility = View.GONE; llTeamInput.visibility = View.GONE

            lifecycleScope.launch {
                val match = try {
                    if (existingMatchId > 0) {
                        withContext(Dispatchers.IO) { SupabaseHelper.getMatchById(existingMatchId) }
                    } else if (fixtureId > 0) {
                        // Fallback: search by fixtureId if we came from tournament but ID wasn't passed directly
                        withContext(Dispatchers.IO) { 
                            SupabaseHelper.getMatchesByFixtureId(fixtureId).firstOrNull() 
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("ScoreActivity", "Failed to load match: ${e.message}", e)
                    Toast.makeText(this@ScoreActivity, "Failed to load match data", Toast.LENGTH_SHORT).show()
                    null
                }
                
                if (match == null) {
                    Toast.makeText(this@ScoreActivity, "Could not load match data. It may have been deleted.", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }
                
                currentMatch = match
                loadPlayers() // Fetch players from tournament if needed

                // 1. Try to restore detailed state from local cache (SharedPreferences)
                restoreLocalState()

                if (currentMatch == null && team1Name.isNotEmpty() && team2Name.isNotEmpty()) {
                    val newMatch = Match(team1Name = team1Name, team2Name = team2Name, matchStatus = "Ongoing", tournamentId = tournamentId, fixtureId = fixtureId)
                    val id = try {
                        withContext(Dispatchers.IO) { SupabaseHelper.addMatch(newMatch) }
                    } catch (e: Exception) {
                        Log.e("ScoreActivity", "Failed to create match: ${e.message}", e)
                        Toast.makeText(this@ScoreActivity, "Failed to create match", Toast.LENGTH_SHORT).show()
                        -1L
                    }
                    newMatch.id = id
                    currentMatch = newMatch
                }

                val m = currentMatch
                val prefs = getSharedPreferences("match_state_${m?.id}", MODE_PRIVATE)
                
                // 2. Innings detection:
                // Priority: 1. Locally restored state (from SharedPreferences), 2. Computed from Match properties
                if (m != null && !prefs.contains("isTeam1Batting")) {
                    isTeam1Batting = if (m.team2Balls > 0 || m.team2Runs > 0 || m.matchStatus.contains("2nd") || m.matchStatus.contains("Innings 2")) {
                        false
                    } else {
                        // If 1st innings is complete, it MUST be 2nd innings
                        m.team1Balls < totalOvers * 6 && m.team1Wickets < 10
                    }
                }

                // 3. Check if match is fundamentally in progress (has runs or balls) OR has batsmen set
                val isInProgress = m != null && (m.team1Balls > 0 || m.team2Balls > 0 || m.currentBatsman1.isNotEmpty() || m.team1Runs > 0 || m.team2Runs > 0)

                if (m != null && isInProgress) {
                    // 4. Fallback: restore active players from cloud if local cache was empty/missing
                    if (striker == null && m.currentBatsman1.isNotEmpty()) {
                        striker = BatsmanInnings(
                            name = m.currentBatsman1,
                            runs = m.batsman1Runs,
                            ballsFaced = m.batsman1Balls,
                            fours = m.batsman1Fours,
                            sixes = m.batsman1Sixes,
                            isOnStrike = true
                        )
                        nonStriker = BatsmanInnings(
                            name = m.currentBatsman2,
                            runs = m.batsman2Runs,
                            ballsFaced = m.batsman2Balls,
                            fours = m.batsman2Fours,
                            sixes = m.batsman2Sixes
                        )
                        currentBowler = BowlerInnings(
                            name = m.currentBowler,
                            ballsBowled = m.bowlerOvers,
                            maidens = m.bowlerMaidens,
                            runsConceded = m.bowlerRuns,
                            wickets = m.bowlerWickets
                        )
                        
                        // Sync restored cloud players into lists
                        if (isTeam1Batting) {
                            if (allBatsmenInnings1.isEmpty()) striker?.let { allBatsmenInnings1.add(it) }
                            if (allBatsmenInnings1.size < 2) nonStriker?.let { allBatsmenInnings1.add(it) }
                            if (usedBatsmen1.isEmpty()) { usedBatsmen1.add(m.currentBatsman1); if (m.currentBatsman2.isNotEmpty()) usedBatsmen1.add(m.currentBatsman2) }
                        } else {
                            if (allBatsmenInnings2.isEmpty()) striker?.let { allBatsmenInnings2.add(it) }
                            if (allBatsmenInnings2.size < 2) nonStriker?.let { allBatsmenInnings2.add(it) }
                            if (usedBatsmen2.isEmpty()) { usedBatsmen2.add(m.currentBatsman1); if (m.currentBatsman2.isNotEmpty()) usedBatsmen2.add(m.currentBatsman2) }
                        }
                        
                        val bowlList = if (isTeam1Batting) allBowlersInnings2 else allBowlersInnings1
                        if (bowlList.isEmpty()) currentBowler?.let { bowlList.add(it) }
                    }

                    if (!isTeam1Batting) {
                        tvTarget.text = "Target: ${m.team1Runs + 1} runs"
                        tvTarget.visibility = View.VISIBLE
                    }

                    // Use team names from the match object as they contain the correct order after toss
                    team1Name = m.team1Name
                    team2Name = m.team2Name

                    lastBowlerName = if (m.lastBowlerName.isNotEmpty()) m.lastBowlerName else null
                    llScoreControls.visibility = View.VISIBLE
                    updateScoreDisplay()
                    Toast.makeText(this@ScoreActivity, "Match resumed!", Toast.LENGTH_SHORT).show()
                } else {
                    if (team1Players.isNotEmpty() || team2Players.isNotEmpty()) {
                        showTossDialog()
                    } else {
                        showOpeningPlayersFromTeamList()
                    }
                }
            }
            return
        }

        btnInitMatch.setOnClickListener {
            val t1 = etTeam1.text.toString().trim()
            val t2 = etTeam2.text.toString().trim()
            if (t1.isEmpty() || t2.isEmpty()) { Toast.makeText(this, "Enter both team names", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (t1.equals(t2, ignoreCase = true)) {
                Toast.makeText(this, "Error: Team names cannot be identical!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            team1Name = t1; team2Name = t2
            
            lifecycleScope.launch {
                val newMatch = Match(team1Name = t1, team2Name = t2, matchStatus = "Ongoing")
                val id = try {
                    withContext(Dispatchers.IO) { SupabaseHelper.addMatch(newMatch) }
                } catch (e: Exception) {
                    Log.e("ScoreActivity", "Failed to create match: ${e.message}", e)
                    Toast.makeText(this@ScoreActivity, "Failed to create match", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                newMatch.id = id
                currentMatch = newMatch
                
                etTeam1.isEnabled = false; etTeam2.isEnabled = false
                btnInitMatch.visibility = View.GONE; llTeamInput.visibility = View.GONE
                
                // Strictly show toss dialog for every new match
                showTossDialog()
            }
        }
    }

    // ========== TOSS ==========
    private fun showTossDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_toss_winner, null)
        val btnTeam1 = dialogView.findViewById<Button>(R.id.btnTossTeam1)
        val btnTeam2 = dialogView.findViewById<Button>(R.id.btnTossTeam2)

        btnTeam1.text = team1Name
        btnTeam2.text = team2Name

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
        dialog.show()

        btnTeam1.setOnClickListener {
            dialog.dismiss()
            showTossChoiceDialog(team1Name)
        }
        btnTeam2.setOnClickListener {
            dialog.dismiss()
            showTossChoiceDialog(team2Name)
        }
    }

    private fun showTossChoiceDialog(tossWinner: String) {
        val tossLoser = if (tossWinner == team1Name) team2Name else team1Name

        val dialogView = layoutInflater.inflate(R.layout.dialog_toss_choice, null)
        val tvLabel = dialogView.findViewById<TextView>(R.id.tvTossWinnerLabel)
        val btnBatting = dialogView.findViewById<Button>(R.id.btnChooseBatting)
        val btnBowling = dialogView.findViewById<Button>(R.id.btnChooseBowling)

        tvLabel.text = "🎉 $tossWinner won the toss!"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
        dialog.show()

        btnBatting.setOnClickListener {
            dialog.dismiss()
            setupInningsOrder(tossWinner, tossLoser)
        }
        btnBowling.setOnClickListener {
            dialog.dismiss()
            setupInningsOrder(tossLoser, tossWinner)
        }
    }

    private fun setupInningsOrder(battingTeam: String, bowlingTeam: String) {
        if (battingTeam == team2Name) {
            currentMatch?.let {
                it.team1Name = team2Name; it.team2Name = team1Name
            }
            val temp = team1Name; team1Name = team2Name; team2Name = temp
            val tempPlayers = ArrayList(team1Players); team1Players = team2Players; team2Players = tempPlayers
        }

        AlertDialog.Builder(this)
            .setTitle("🏏 Let's Play!")
            .setMessage("$battingTeam will bat first\n$bowlingTeam will bowl first\n\n$totalOvers overs match")
            .setCancelable(true)
            .setPositiveButton("Start Scoring") { _, _ ->
                showOpeningPlayersFromTeamList()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    // ========== PLAYER SELECTION FROM TEAM LIST ==========
    private fun showOpeningPlayersFromTeamList() {
        val battingPlayers = if (isTeam1Batting) team1Players else team2Players
        val bowlingPlayers = if (isTeam1Batting) team2Players else team1Players

        if (battingPlayers.isEmpty() || bowlingPlayers.isEmpty()) {
            showOpeningPlayersInputForCustomTeams()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_opening_players_list, null)
        val spStriker = dialogView.findViewById<Spinner>(R.id.spStriker)
        val spNonStriker = dialogView.findViewById<Spinner>(R.id.spNonStriker)
        val spBowler = dialogView.findViewById<Spinner>(R.id.spBowler)

        val adapter1 = ArrayAdapter(this, R.layout.item_spinner, battingPlayers)
        adapter1.setDropDownViewResource(R.layout.item_spinner)
        spStriker.adapter = adapter1

        val adapter2 = ArrayAdapter(this, R.layout.item_spinner, battingPlayers)
        adapter2.setDropDownViewResource(R.layout.item_spinner)
        spNonStriker.adapter = adapter2

        val adapter3 = ArrayAdapter(this, R.layout.item_spinner, bowlingPlayers)
        adapter3.setDropDownViewResource(R.layout.item_spinner)
        spBowler.adapter = adapter3

        if (battingPlayers.size >= 2) spNonStriker.setSelection(1)

        AlertDialog.Builder(this)
            .setTitle("Select Opening Players")
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Start") { _, _ ->
                val b1 = spStriker.selectedItem?.toString() ?: ""
                val b2 = spNonStriker.selectedItem?.toString() ?: ""
                val bow = spBowler.selectedItem?.toString() ?: ""

                if (b1 == b2) {
                    Toast.makeText(this, "Striker and non-striker must be different!", Toast.LENGTH_SHORT).show()
                    showOpeningPlayersFromTeamList()
                    return@setPositiveButton
                }

                striker = BatsmanInnings(name = b1, isOnStrike = true)
                nonStriker = BatsmanInnings(name = b2)

                val usedList = if (isTeam1Batting) usedBatsmen1 else usedBatsmen2
                usedList.add(b1); usedList.add(b2)

                if (isTeam1Batting) { striker?.let { allBatsmenInnings1.add(it) }; nonStriker?.let { allBatsmenInnings1.add(it) } }
                else { striker?.let { allBatsmenInnings2.add(it) }; nonStriker?.let { allBatsmenInnings2.add(it) } }

                currentBowler = BowlerInnings(name = bow)
                val cBow = currentBowler
                if (cBow != null) {
                    if (isTeam1Batting) allBowlersInnings2.add(cBow)
                    else allBowlersInnings1.add(cBow)
                }

                llScoreControls.visibility = View.VISIBLE
                syncAndSave()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }



    private fun showNewBatsmanFromList() {
        val battingPlayers = if (isTeam1Batting) team1Players else team2Players
        if (battingPlayers.isEmpty()) {
            showNewBatsmanInputForCustomTeams()
            return
        }
        val usedList = if (isTeam1Batting) usedBatsmen1 else usedBatsmen2
        val available = battingPlayers.filter { it !in usedList }

        if (available.isEmpty()) {
            Toast.makeText(this, "No more players available in the squad!", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select New Batsman")
            .setCancelable(true)
            .setItems(available.toTypedArray()) { _, which ->
                val name = available[which]
                usedList.add(name)
                val newBat = BatsmanInnings(name = name, isOnStrike = true)
                val batList = if (isTeam1Batting) allBatsmenInnings1 else allBatsmenInnings2
                batList.add(newBat)
                striker = newBat
                syncAndSave()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showNewBowlerFromList() {
        val bowlingPlayers = if (isTeam1Batting) team2Players else team1Players

        if (bowlingPlayers.isEmpty()) {
            showNewBowlerDialogForCustomTeams()
            return
        }

        val filteredBowlers = bowlingPlayers.filter { it != lastBowlerName }

        if (filteredBowlers.isEmpty()) {
            showNewBowlerDialogForCustomTeams()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select New Bowler")
            .setCancelable(true)
            .setItems(filteredBowlers.toTypedArray()) { _, which ->
                val name = filteredBowlers[which]
                val bowlerList = if (isTeam1Batting) allBowlersInnings2 else allBowlersInnings1
                val existing = bowlerList.find { it.name.equals(name, ignoreCase = true) }
                currentBowler = existing ?: BowlerInnings(name = name).also { bowlerList.add(it) }
                syncAndSave()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showOpeningPlayersInputForCustomTeams() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_opening_players_input, null)
        val etStriker = dialogView.findViewById<EditText>(R.id.etDialogStriker)
        val etNonStriker = dialogView.findViewById<EditText>(R.id.etDialogNonStriker)
        val etBowler = dialogView.findViewById<EditText>(R.id.etDialogBowler)

        AlertDialog.Builder(this)
            .setTitle("Select Opening Players")
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Start") { _, _ ->
                val b1 = etStriker.text.toString().trim().ifEmpty { "Batsman 1" }
                val b2 = etNonStriker.text.toString().trim().ifEmpty { "Batsman 2" }
                val bow = etBowler.text.toString().trim().ifEmpty { "Bowler 1" }

                if (b1.equals(b2, ignoreCase = true)) {
                    Toast.makeText(this, "Striker and non-striker must be different!", Toast.LENGTH_SHORT).show()
                    showOpeningPlayersInputForCustomTeams()
                    return@setPositiveButton
                }

                striker = BatsmanInnings(name = b1, isOnStrike = true)
                nonStriker = BatsmanInnings(name = b2)

                val usedList = if (isTeam1Batting) usedBatsmen1 else usedBatsmen2
                usedList.add(b1); usedList.add(b2)

                if (isTeam1Batting) {
                    allBatsmenInnings1.add(striker!!)
                    allBatsmenInnings1.add(nonStriker!!)
                } else {
                    allBatsmenInnings2.add(striker!!)
                    allBatsmenInnings2.add(nonStriker!!)
                }

                currentBowler = BowlerInnings(name = bow)
                val cBow = currentBowler
                if (cBow != null) {
                    if (isTeam1Batting) allBowlersInnings2.add(cBow)
                    else allBowlersInnings1.add(cBow)
                }

                llScoreControls.visibility = View.VISIBLE
                syncAndSave()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showNewBatsmanInputForCustomTeams() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_batsman_input, null)
        val etNewBatsman = dialogView.findViewById<EditText>(R.id.etDialogNewBatsman)

        AlertDialog.Builder(this)
            .setTitle("Select New Batsman")
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Confirm") { _, _ ->
                val name = etNewBatsman.text.toString().trim().ifEmpty { "New Batsman" }
                val usedList = if (isTeam1Batting) usedBatsmen1 else usedBatsmen2
                usedList.add(name)
                val newBat = BatsmanInnings(name = name, isOnStrike = true)
                val batList = if (isTeam1Batting) allBatsmenInnings1 else allBatsmenInnings2
                batList.add(newBat)
                striker = newBat
                syncAndSave()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showNewBowlerDialogForCustomTeams() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_bowler, null)
        val etNewBowler = dialogView.findViewById<EditText>(R.id.etNewBowler)
        val llPrevBowlers = dialogView.findViewById<LinearLayout>(R.id.llPrevBowlers)
        val spinnerPrevBowlers = dialogView.findViewById<Spinner>(R.id.spinnerPrevBowlers)

        val bowlerList = if (isTeam1Batting) allBowlersInnings2 else allBowlersInnings1
        val prevBowlers = bowlerList.map { it.name }.filter { it != lastBowlerName }

        if (prevBowlers.isNotEmpty()) {
            llPrevBowlers.visibility = View.VISIBLE
            val spinnerAdapter = ArrayAdapter(this, R.layout.item_spinner, listOf("--- Select Previous Bowler ---") + prevBowlers)
            spinnerAdapter.setDropDownViewResource(R.layout.item_spinner)
            spinnerPrevBowlers.adapter = spinnerAdapter
        } else {
            llPrevBowlers.visibility = View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Select New Bowler")
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Select") { _, _ ->
                val typedName = etNewBowler.text.toString().trim()
                val selectedPos = spinnerPrevBowlers.selectedItemPosition
                val name = if (typedName.isNotEmpty()) {
                    typedName
                } else if (llPrevBowlers.visibility == View.VISIBLE && selectedPos > 0) {
                    prevBowlers[selectedPos - 1]
                } else {
                    "Bowler"
                }

                if (name == lastBowlerName) {
                    Toast.makeText(this, "A bowler cannot bowl consecutive overs!", Toast.LENGTH_LONG).show()
                    showNewBowlerDialogForCustomTeams()
                    return@setPositiveButton
                }

                val existing = bowlerList.find { it.name.equals(name, ignoreCase = true) }
                currentBowler = existing ?: BowlerInnings(name = name).also { bowlerList.add(it) }
                syncAndSave()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }



    // ========== SCORING ==========
    private fun addLegalDelivery(runs: Int, label: String) {
        currentMatch?.let {
            val currentBalls = if (isTeam1Batting) it.team1Balls else it.team2Balls
            val maxBalls = totalOvers * 6
            if (currentBalls >= maxBalls) {
                if (isTeam1Batting) { handleInningsChange(); updateScoreDisplay() }
                else handleMatchFinish()
                return
            }

            saveSnapshot()

            if (isTeam1Batting) { it.team1Runs += runs; it.team1Balls += 1 }
            else { it.team2Runs += runs; it.team2Balls += 1 }

            striker?.let { s -> s.runs += runs; s.ballsFaced += 1; if (runs == 4) s.fours++; if (runs == 6) s.sixes++ }
            currentBowler?.let { b -> b.ballsBowled++; b.runsConceded += runs; b.currentOverRuns += runs; b.currentOverLegalBalls++ }

            currentOverBalls.add(label)

            val updatedBalls = if (isTeam1Batting) it.team1Balls else it.team2Balls

            if (updatedBalls % 6 == 0) {
                currentBowler?.let { b ->
                    if (b.currentOverRuns == 0) b.maidens++
                    b.currentOverRuns = 0; b.currentOverLegalBalls = 0
                    lastBowlerName = b.name
                    currentMatch?.lastBowlerName = b.name
                }
                currentOverBalls.clear()

                if (updatedBalls >= maxBalls) {
                    syncAndSave()
                    Toast.makeText(this, "Innings complete! $totalOvers overs done.", Toast.LENGTH_LONG).show()
                    if (isTeam1Batting) { handleInningsChange(); updateScoreDisplay() }
                    else handleMatchFinish()
                    return
                }

                Toast.makeText(this, "Over ${updatedBalls / 6} complete!", Toast.LENGTH_SHORT).show()
                rotateStrike()
                showNewBowlerFromList()
            } else {
                if (runs % 2 == 1) rotateStrike()
            }

            syncAndSave()
            checkSecondInningsTarget()
        }
    }

    private fun handleExtra(type: String) {
        val options = arrayOf("$type (0 extra runs)", "$type + 1 run", "$type + 2 runs", "$type + 3 runs", "$type + 4 runs", "$type + 6 runs")
        var selectedOption = 0
        AlertDialog.Builder(this)
            .setTitle("Select Extra Runs")
            .setSingleChoiceItems(options, 0) { _, which -> selectedOption = which }
            .setPositiveButton("Add") { _, _ ->
                val extraRuns = when (selectedOption) {
                    0 -> 0; 1 -> 1; 2 -> 2; 3 -> 3; 4 -> 4; 5 -> 6; else -> 0
                }
                currentMatch?.let {
                    saveSnapshot()
                    val totalRuns = 1 + extraRuns 
                    if (isTeam1Batting) { it.team1Runs += totalRuns; it.team1Extras += 1 }
                    else { it.team2Runs += totalRuns; it.team2Extras += 1 }
                    
                    currentBowler?.let { b -> b.runsConceded += totalRuns; b.currentOverRuns += totalRuns }
                    if (extraRuns > 0 && type == "Nb") {
                        striker?.let { s -> s.runs += extraRuns }
                    }
                    
                    currentOverBalls.add(if (extraRuns > 0) "$type+$extraRuns" else type)
                    
                    if (extraRuns % 2 == 1) rotateStrike()
                    
                    syncAndSave()
                    checkSecondInningsTarget()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rotateStrike() {
        val temp = striker; striker = nonStriker; nonStriker = temp
        striker?.isOnStrike = true; nonStriker?.isOnStrike = false
    }

    private fun showDismissalDialog() {
        AlertDialog.Builder(this)
            .setTitle("Dismissal Type")
            .setItems(arrayOf("Bowled", "Caught", "LBW", "Run Out", "Stumped", "Hit Wicket")) { _, which ->
                handleWicket(arrayOf("Bowled", "Caught", "LBW", "Run Out", "Stumped", "Hit Wicket")[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleWicket(dismissalType: String) {
        currentMatch?.let {
            saveSnapshot()

            if (isTeam1Batting) { it.team1Balls++; it.team1Wickets++ }
            else { it.team2Balls++; it.team2Wickets++ }

            striker?.let { s -> s.ballsFaced++; s.isOut = true; s.dismissalInfo = dismissalType }
            currentBowler?.let { b ->
                b.ballsBowled++; b.currentOverLegalBalls++
                if (dismissalType != "Run Out") b.wickets++
            }

            currentOverBalls.add("W")

            val currentBalls = if (isTeam1Batting) it.team1Balls else it.team2Balls
            val maxBalls = totalOvers * 6
            if (currentBalls % 6 == 0) {
                currentBowler?.let { b -> 
                    if (b.currentOverRuns == 0) b.maidens++
                    b.currentOverRuns = 0; b.currentOverLegalBalls = 0
                    lastBowlerName = b.name
                    currentMatch?.lastBowlerName = b.name
                }
                    currentOverBalls.clear()
            }

            Toast.makeText(this, "$dismissalType! Wicket!", Toast.LENGTH_SHORT).show()

            val currentWickets = if (isTeam1Batting) it.team1Wickets else it.team2Wickets
        val playingSize = if (isTeam1Batting) team1Players.size else team2Players.size
        val allOutWickets = if (playingSize > 0) playingSize - 1 else 10

        if (isTeam1Batting && it.team1Wickets >= allOutWickets) {
            Toast.makeText(this, "ALL OUT!", Toast.LENGTH_LONG).show()
            syncAndSave(); handleInningsChange()
        } else if (!isTeam1Batting && it.team2Wickets >= allOutWickets) {
            Toast.makeText(this, "ALL OUT!", Toast.LENGTH_LONG).show()
            syncAndSave(); handleMatchFinish()
        } else if (currentBalls >= maxBalls) {
                syncAndSave()
                if (isTeam1Batting) handleInningsChange()
                else handleMatchFinish()
            } else {
                syncAndSave()
                showNewBatsmanFromList()
                if (currentBalls % 6 == 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isFinishing || isDestroyed) return@postDelayed
                        showNewBowlerFromList()
                    }, 500)
                }
            }
            checkSecondInningsTarget()
        }
    }

    private fun checkSecondInningsTarget() {
        currentMatch?.let {
            if (!isTeam1Batting) {
                val playingSize = team2Players.size
                val allOutWickets = if (playingSize > 0) playingSize - 1 else 10
                
                if (it.team2Runs > it.team1Runs) {
                    handleMatchFinish()
                } else if (it.team2Wickets >= allOutWickets || it.team2Balls >= totalOvers * 6) {
                    handleMatchFinish()
                }
            }
        }
    }

    private fun handleInningsChange() {
        isTeam1Batting = false
        currentOverBalls.clear()
        undoStack.clear()
        lastBowlerName = null
        currentMatch?.lastBowlerName = ""
        currentMatch?.let {
            tvTarget.text = "Target: ${it.team1Runs + 1} runs"
            tvTarget.visibility = View.VISIBLE
        }
        Toast.makeText(this, "Innings Change! ${currentMatch?.team2Name} to bat.", Toast.LENGTH_LONG).show()

        if (team1Players.isNotEmpty() || team2Players.isNotEmpty()) {
            showOpeningPlayersFromTeamList()
        } else {
            show2ndInningsPlayersDialog()
        }
    }

    private fun show2ndInningsPlayersDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_opening_players, null)
        val etBatsman1 = dialogView.findViewById<EditText>(R.id.etOpeningBatsman1)
        val etBatsman2 = dialogView.findViewById<EditText>(R.id.etOpeningBatsman2)
        val etBowler = dialogView.findViewById<EditText>(R.id.etOpeningBowler)

        AlertDialog.Builder(this)
            .setTitle("2nd Innings — Opening Players")
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Start 2nd Innings") { _, _ ->
                val b1 = etBatsman1.text.toString().trim().ifEmpty { "Batsman 1" }
                val b2 = etBatsman2.text.toString().trim().ifEmpty { "Batsman 2" }
                val bow = etBowler.text.toString().trim().ifEmpty { "Bowler 1" }

                if (b1.equals(b2, ignoreCase = true)) {
                    Toast.makeText(this, "Striker and non-striker must be different!", Toast.LENGTH_SHORT).show()
                    show2ndInningsPlayersDialog()
                    return@setPositiveButton
                }

                striker = BatsmanInnings(name = b1, isOnStrike = true)
                nonStriker = BatsmanInnings(name = b2)
                striker?.let { allBatsmenInnings2.add(it) }
                nonStriker?.let { allBatsmenInnings2.add(it) }
                currentBowler = BowlerInnings(name = bow)
                currentBowler?.let { allBowlersInnings1.add(it) }
                syncAndSave()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun handleMatchFinish() {
    currentMatch?.let { match ->
        if (match.team1Runs == match.team2Runs) {
            AlertDialog.Builder(this)
                .setTitle("Match Tied!")
                .setMessage("The scores are level. Do you want to start a Super Over?")
                .setPositiveButton("Start Super Over") { _, _ -> resetForSuperOver() }
                .setNegativeButton("Declare Draw") { _, _ -> 
                    match.matchStatus = "Finished"
                    match.winner = "Match Tied"
                    lifecycleScope.launch {
                        syncMatchWithPlayerData()
                        try {
                            withContext(Dispatchers.IO) { SupabaseHelper.updateMatch(match) }
                        } catch (e: Exception) {
                            Log.e("ScoreActivity", "Failed to update match (tied): ${e.message}", e)
                        }
                        try {
                            if (fixtureId > 0) withContext(Dispatchers.IO) { SupabaseHelper.updateFixtureStatus(fixtureId, "Completed") }
                        } catch (e: Exception) {
                            Log.e("ScoreActivity", "Failed to update fixture status: ${e.message}", e)
                        }
                        clearLocalState()
                        Log.d("ScoreActivity", "Match FINISHED (Tied): id=${match.id} status=${match.matchStatus} winner=${match.winner}")
                        finish()
                    }
                }
                .setCancelable(false)
                .show()
            return
        }

        match.matchStatus = "Finished"
        match.winner = when {
            match.team1Runs > match.team2Runs -> "${match.team1Name} won by ${match.team1Runs - match.team2Runs} runs"
            match.team2Runs > match.team1Runs -> {
                val playingSize = team2Players.size
                val totalWicketsPossible = if (playingSize > 0) playingSize - 1 else 10
                "${match.team2Name} won by ${totalWicketsPossible - match.team2Wickets} wickets"
            }
            else -> "Match Tied"
        }
        
        Log.d("ScoreActivity", "Match FINISHED: id=${match.id} status=${match.matchStatus} winner=${match.winner} t1=${match.team1Runs}/${match.team1Wickets}(${match.team1Balls}b) t2=${match.team2Runs}/${match.team2Wickets}(${match.team2Balls}b) tid=${match.tournamentId}")
        
        lifecycleScope.launch {
            syncMatchWithPlayerData()
            try {
                val updateResult = withContext(Dispatchers.IO) { SupabaseHelper.updateMatch(match) }
                Log.d("ScoreActivity", "Match update result: $updateResult for match id=${match.id}")
            } catch (e: Exception) {
                Log.e("ScoreActivity", "Failed to update match: ${e.message}", e)
            }
            try {
                if (fixtureId > 0) withContext(Dispatchers.IO) { SupabaseHelper.updateFixtureStatus(fixtureId, "Completed") }
            } catch (e: Exception) {
                Log.e("ScoreActivity", "Failed to update fixture status: ${e.message}", e)
            }
            clearLocalState()

            if (tournamentId > 0 && fixtureId > 0) {
                try {
                    advanceKnockoutWinner(match)
                } catch (e: Exception) {
                    Log.e("ScoreActivity", "Failed to advance knockout winner: ${e.message}", e)
                }
            }

            AlertDialog.Builder(this@ScoreActivity)
                .setTitle("Match Finished!")
                .setMessage(match.winner)
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }
}

private fun clearLocalState() {
    val m = currentMatch ?: return
    val prefs = getSharedPreferences("match_state_${m.id}", MODE_PRIVATE)
    prefs.edit().clear().apply()
    Log.d("ScoreActivity", "Local state cleared for match ${m.id}")
}

    private fun resetForSuperOver() {
        // Reset all match stats for a 1-over shootout
        currentMatch?.let { m ->
            m.team1Runs = 0; m.team1Wickets = 0; m.team1Balls = 0; m.team1Extras = 0
            m.team2Runs = 0; m.team2Wickets = 0; m.team2Balls = 0; m.team2Extras = 0
            m.matchStatus = "Ongoing (Super Over)"
            m.winner = ""
            m.currentBatsman1 = ""; m.currentBatsman2 = ""; m.currentBowler = ""; m.lastBowlerName = ""
        }
        
        totalOvers = 1
        isTeam1Batting = true
        striker = null; nonStriker = null; currentBowler = null
        allBatsmenInnings1.clear(); allBowlersInnings1.clear()
        allBatsmenInnings2.clear(); allBowlersInnings2.clear()
        usedBatsmen1.clear(); usedBatsmen2.clear()
        currentOverBalls.clear()
        undoStack.clear()
        
        tvTarget.visibility = View.GONE
        llScoreControls.visibility = View.GONE
        updateScoreDisplay()
        
        Toast.makeText(this, "Super Over Initialized! (1 Over)", Toast.LENGTH_LONG).show()
        showTossDialog()
    }

    private suspend fun advanceKnockoutWinner(match: Match) {
        val tournament = try {
            withContext(Dispatchers.IO) { SupabaseHelper.getTournamentById(tournamentId) }
        } catch (e: Exception) {
            Log.e("ScoreActivity", "Failed to get tournament: ${e.message}", e)
            return
        } ?: return
        if (tournament.format != "Knockout") return

        val fixtures = try {
            withContext(Dispatchers.IO) { SupabaseHelper.getFixturesForTournament(tournamentId) }
        } catch (e: Exception) {
            Log.e("ScoreActivity", "Failed to get fixtures: ${e.message}", e)
            return
        }
        val currentFixture = fixtures.find { it.id == fixtureId } ?: return

        val winnerName = when {
            match.team1Runs > match.team2Runs -> match.team1Name
            match.team2Runs > match.team1Runs -> match.team2Name
            else -> return 
        }

        // Find the next fixture that has a TBD slot and is in a later round
        // TBD slots are filled in order of match_number
        try {
            withContext(Dispatchers.IO) {
                val allFixtures = SupabaseHelper.getFixturesForTournament(tournamentId)
                for (f in allFixtures) {
                    if (f.matchNumber <= currentFixture.matchNumber) continue
                    var changed = false
                    if (f.team1Name == "TBD") {
                        f.team1Name = winnerName
                        changed = true
                    } else if (f.team2Name == "TBD") {
                        f.team2Name = winnerName
                        changed = true
                    }
                    if (changed) {
                        SupabaseHelper.updateFixture(f)
                        break // Only fill one slot per match win
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScoreActivity", "Failed to advance knockout winner: ${e.message}", e)
        }
    }

    // ========== UNDO ==========
    private fun saveSnapshot() {
        val m = currentMatch ?: return
        undoStack.add(ScoringSnapshot(
            team1Runs = m.team1Runs, team1Wickets = m.team1Wickets,
            team1Balls = m.team1Balls, team1Extras = m.team1Extras,
            team2Runs = m.team2Runs, team2Wickets = m.team2Wickets,
            team2Balls = m.team2Balls, team2Extras = m.team2Extras,
            isTeam1Batting = isTeam1Batting,
            strikerName = striker?.name ?: "", strikerRuns = striker?.runs ?: 0,
            strikerBalls = striker?.ballsFaced ?: 0, strikerFours = striker?.fours ?: 0,
            strikerSixes = striker?.sixes ?: 0,
            nonStrikerName = nonStriker?.name ?: "", nonStrikerRuns = nonStriker?.runs ?: 0,
            nonStrikerBalls = nonStriker?.ballsFaced ?: 0, nonStrikerFours = nonStriker?.fours ?: 0,
            nonStrikerSixes = nonStriker?.sixes ?: 0,
            bowlerName = currentBowler?.name ?: "", bowlerBalls = currentBowler?.ballsBowled ?: 0,
            bowlerMaidens = currentBowler?.maidens ?: 0, bowlerRunsConceded = currentBowler?.runsConceded ?: 0,
            bowlerWickets = currentBowler?.wickets ?: 0,
            bowlerCurrentOverRuns = currentBowler?.currentOverRuns ?: 0,
            bowlerCurrentOverLegalBalls = currentBowler?.currentOverLegalBalls ?: 0,
            overBalls = ArrayList(currentOverBalls),
            lastBowlerName = lastBowlerName ?: ""
        ))
        if (undoStack.size > 50) undoStack.removeAt(0)
    }

    private fun performUndo() {
        if (undoStack.isEmpty()) { Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show(); return }
        val snap = undoStack.removeAt(undoStack.size - 1)
        currentMatch?.let {
            it.team1Runs = snap.team1Runs; it.team1Wickets = snap.team1Wickets
            it.team1Balls = snap.team1Balls; it.team1Extras = snap.team1Extras
            it.team2Runs = snap.team2Runs; it.team2Wickets = snap.team2Wickets
            it.team2Balls = snap.team2Balls; it.team2Extras = snap.team2Extras
        }
        isTeam1Batting = snap.isTeam1Batting
        striker?.let { it.name = snap.strikerName; it.runs = snap.strikerRuns; it.ballsFaced = snap.strikerBalls; it.fours = snap.strikerFours; it.sixes = snap.strikerSixes }
        nonStriker?.let { it.name = snap.nonStrikerName; it.runs = snap.nonStrikerRuns; it.ballsFaced = snap.nonStrikerBalls; it.fours = snap.nonStrikerFours; it.sixes = snap.nonStrikerSixes }
        currentBowler?.let { it.name = snap.bowlerName; it.ballsBowled = snap.bowlerBalls; it.maidens = snap.bowlerMaidens; it.runsConceded = snap.bowlerRunsConceded; it.wickets = snap.bowlerWickets; it.currentOverRuns = snap.bowlerCurrentOverRuns; it.currentOverLegalBalls = snap.bowlerCurrentOverLegalBalls }
        currentOverBalls.clear(); currentOverBalls.addAll(snap.overBalls)
        lastBowlerName = snap.lastBowlerName.ifEmpty { null }
        currentMatch?.lastBowlerName = snap.lastBowlerName
        syncAndSave()
        Toast.makeText(this, "Undone!", Toast.LENGTH_SHORT).show()
    }

    // ========== SYNC & DISPLAY ==========
    private fun syncAndSave() {
        updateScoreDisplay() // Optimistic UI Update: refresh UI immediately before network sync
        lifecycleScope.launch {
            syncMatchWithPlayerData()
            val m = currentMatch
            if (m != null) {
                try {
                    withContext(Dispatchers.IO) { SupabaseHelper.updateMatch(m) }
                } catch (e: Exception) {
                    Log.e("ScoreActivity", "syncAndSave failed: ${e.message}", e)
                }
                // updateScoreDisplay() // Removed from here to avoid laggy refresh
            }
        }
    }

    private fun syncMatchWithPlayerData() {
        currentMatch?.let { m ->
            striker?.let { 
                m.currentBatsman1 = it.name
                m.batsman1Runs = it.runs
                m.batsman1Balls = it.ballsFaced
                m.batsman1Fours = it.fours
                m.batsman1Sixes = it.sixes
            }
            nonStriker?.let { 
                m.currentBatsman2 = it.name
                m.batsman2Runs = it.runs
                m.batsman2Balls = it.ballsFaced
                m.batsman2Fours = it.fours
                m.batsman2Sixes = it.sixes
            }
            currentBowler?.let { 
                m.currentBowler = it.name
                m.bowlerOvers = it.ballsBowled
                m.bowlerMaidens = it.maidens
                m.bowlerRuns = it.runsConceded
                m.bowlerWickets = it.wickets 
            }
            m.lastBowlerName = lastBowlerName ?: ""
            saveLocalState()
        }
    }

    private fun saveLocalState() {
        val m = currentMatch ?: return
        val prefs = getSharedPreferences("match_state_${m.id}", MODE_PRIVATE)
        prefs.edit().apply {
            // Player innings lists
            putString("used1", gson.toJson(usedBatsmen1))
            putString("used2", gson.toJson(usedBatsmen2))
            putString("batInnings1", gson.toJson(allBatsmenInnings1))
            putString("batInnings2", gson.toJson(allBatsmenInnings2))
            putString("bowlInnings1", gson.toJson(allBowlersInnings1))
            putString("bowlInnings2", gson.toJson(allBowlersInnings2))
            putString("overBalls", gson.toJson(currentOverBalls))
            putBoolean("isTeam1Batting", isTeam1Batting)

            // Match-level scores — the critical data that was missing
            putInt("team1Runs", m.team1Runs)
            putInt("team1Wickets", m.team1Wickets)
            putInt("team1Balls", m.team1Balls)
            putInt("team2Runs", m.team2Runs)
            putInt("team2Wickets", m.team2Wickets)
            putInt("team2Balls", m.team2Balls)
            putInt("team1Extras", m.team1Extras)
            putInt("team2Extras", m.team2Extras)
            putString("matchStatus", m.matchStatus)
            putString("team1Name", m.team1Name)
            putString("team2Name", m.team2Name)

            // Active player names for rebinding
            putString("strikerName", striker?.name ?: "")
            putString("nonStrikerName", nonStriker?.name ?: "")
            putString("bowlerName", currentBowler?.name ?: "")
            putString("lastBowlerName", lastBowlerName ?: "")

            apply()
        }
        Log.d("ScoreActivity", "Local state saved for match ${m.id}: ${m.team1Runs}/${m.team1Wickets} (${m.team1Balls}b)")
    }

    private fun restoreLocalState() {
        val m = currentMatch ?: return
        val prefs = getSharedPreferences("match_state_${m.id}", MODE_PRIVATE)
        if (!prefs.contains("isTeam1Batting")) return

        val cachedTeam1 = prefs.getString("team1Name", "") ?: ""
        val cachedTeam2 = prefs.getString("team2Name", "") ?: ""
        if (cachedTeam1.isNotEmpty() && cachedTeam2.isNotEmpty()) {
            if (!cachedTeam1.equals(m.team1Name, ignoreCase = true) || !cachedTeam2.equals(m.team2Name, ignoreCase = true)) {
                Log.w("ScoreActivity", "Team names in cache ($cachedTeam1 vs $cachedTeam2) do not match database (${m.team1Name} vs ${m.team2Name}). Clearing stale cache.")
                prefs.edit().clear().apply()
                return
            }
        }

        try {
            val typeStr = object : com.google.gson.reflect.TypeToken<MutableList<String>>() {}.type
            val typeBat = object : com.google.gson.reflect.TypeToken<MutableList<BatsmanInnings>>() {}.type
            val typeBowl = object : com.google.gson.reflect.TypeToken<MutableList<BowlerInnings>>() {}.type

            usedBatsmen1.clear(); usedBatsmen1.addAll(gson.fromJson(prefs.getString("used1", "[]"), typeStr))
            usedBatsmen2.clear(); usedBatsmen2.addAll(gson.fromJson(prefs.getString("used2", "[]"), typeStr))
            
            allBatsmenInnings1.clear(); allBatsmenInnings1.addAll(gson.fromJson(prefs.getString("batInnings1", "[]"), typeBat))
            allBatsmenInnings2.clear(); allBatsmenInnings2.addAll(gson.fromJson(prefs.getString("batInnings2", "[]"), typeBat))
            
            allBowlersInnings1.clear(); allBowlersInnings1.addAll(gson.fromJson(prefs.getString("bowlInnings1", "[]"), typeBowl))
            allBowlersInnings2.clear(); allBowlersInnings2.addAll(gson.fromJson(prefs.getString("bowlInnings2", "[]"), typeBowl))
            
            currentOverBalls.clear(); currentOverBalls.addAll(gson.fromJson(prefs.getString("overBalls", "[]"), typeStr))
            
            isTeam1Batting = prefs.getBoolean("isTeam1Batting", true)

            // Restore match-level scores from local cache (overrides Supabase data which may be stale)
            if (prefs.contains("team1Runs")) {
                m.team1Runs = prefs.getInt("team1Runs", 0)
                m.team1Wickets = prefs.getInt("team1Wickets", 0)
                m.team1Balls = prefs.getInt("team1Balls", 0)
                m.team2Runs = prefs.getInt("team2Runs", 0)
                m.team2Wickets = prefs.getInt("team2Wickets", 0)
                m.team2Balls = prefs.getInt("team2Balls", 0)
                m.team1Extras = prefs.getInt("team1Extras", 0)
                m.team2Extras = prefs.getInt("team2Extras", 0)
                m.team1Name = prefs.getString("team1Name", m.team1Name) ?: m.team1Name
                m.team2Name = prefs.getString("team2Name", m.team2Name) ?: m.team2Name
            }

            // Re-bind active striker/non-striker/bowler objects from the innings list
            val savedStriker = prefs.getString("strikerName", "") ?: ""
            val savedNonStriker = prefs.getString("nonStrikerName", "") ?: ""
            val savedBowler = prefs.getString("bowlerName", "") ?: ""
            lastBowlerName = prefs.getString("lastBowlerName", null)

            val batList = if (isTeam1Batting) allBatsmenInnings1 else allBatsmenInnings2
            striker = batList.find { it.name == savedStriker } ?: batList.find { it.name == m.currentBatsman1 }
            nonStriker = batList.find { it.name == savedNonStriker } ?: batList.find { it.name == m.currentBatsman2 }
            
            val bowlList = if (isTeam1Batting) allBowlersInnings2 else allBowlersInnings1
            currentBowler = bowlList.find { it.name == savedBowler } ?: bowlList.find { it.name == m.currentBowler }

            Log.d("ScoreActivity", "Local state restored for match ${m.id}: ${m.team1Runs}/${m.team1Wickets} (${m.team1Balls}b), striker=${striker?.name}, bowler=${currentBowler?.name}")
        } catch (e: Exception) { Log.e("ScoreActivity", "Restore state failed: ${e.message}") }
    }

    override fun onPause() {
        super.onPause()
        // Emergency save: persist everything before the activity goes to background
        // But skip if the match is already finished — handleMatchFinish already handled Supabase sync
        currentMatch?.let { m ->
            if (m.matchStatus == "Finished") return@let
            syncMatchWithPlayerData()
            saveLocalState()
            // Fire-and-forget Supabase sync
            lifecycleScope.launch {
                try {
                    withContext(NonCancellable + Dispatchers.IO) {
                        SupabaseHelper.updateMatch(m)
                    }
                } catch (e: Exception) {
                    Log.e("ScoreActivity", "onPause sync failed: ${e.message}", e)
                }
            }
        }
    }

    private fun updateScoreDisplay() {
        currentMatch?.let {
            if (isTeam1Batting) {
                tvCurrentBatting.text = "BATTING: ${it.team1Name}"
                tvLiveScore.text = "${it.team1Runs}/${it.team1Wickets}"
                tvOversDisplay.text = "Overs: ${it.getTeam1OversDisplay()} / $totalOvers"
                tvRunRate.text = "RR: ${it.getTeam1RunRate()}"
                tvExtras.text = "Extras: ${it.team1Extras}"
            } else {
                tvCurrentBatting.text = "BATTING: ${it.team2Name}"
                tvLiveScore.text = "${it.team2Runs}/${it.team2Wickets}"
                tvOversDisplay.text = "Overs: ${it.getTeam2OversDisplay()} / $totalOvers"
                tvRunRate.text = "RR: ${it.getTeam2RunRate()}"
                tvExtras.text = "Extras: ${it.team2Extras}"
            }
        }
        updateBatsmanDisplay(); updateBowlerDisplay(); updateThisOverDisplay()
    }

    private fun updateBatsmanDisplay() {
        striker?.let { tvStrikerName.text = "★ ${it.name}"; tvStrikerRuns.text = "${it.runs}"; tvStrikerBalls.text = "${it.ballsFaced}"; tvStrikerSR.text = it.getStrikeRate() }
            ?: run { tvStrikerName.text = "★ —"; tvStrikerRuns.text = "0"; tvStrikerBalls.text = "0"; tvStrikerSR.text = "0.00" }
        nonStriker?.let { tvNonStrikerName.text = it.name; tvNonStrikerRuns.text = "${it.runs}"; tvNonStrikerBalls.text = "${it.ballsFaced}"; tvNonStrikerSR.text = it.getStrikeRate() }
            ?: run { tvNonStrikerName.text = "—"; tvNonStrikerRuns.text = "0"; tvNonStrikerBalls.text = "0"; tvNonStrikerSR.text = "0.00" }
    }

    private fun updateBowlerDisplay() {
        currentBowler?.let { tvBowlerName.text = it.name; tvBowlerOvers.text = it.getOversDisplay(); tvBowlerRuns.text = "${it.runsConceded}"; tvBowlerWickets.text = "${it.wickets}" }
            ?: run { tvBowlerName.text = "—"; tvBowlerOvers.text = "0.0"; tvBowlerRuns.text = "0"; tvBowlerWickets.text = "0" }
    }

    private fun updateThisOverDisplay() {
        llThisOver.removeAllViews()
        for (ball in currentOverBalls) {
            val tv = TextView(this).apply {
                text = ball; textSize = 16f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
                val size = (36 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(4, 0, 4, 0) }
                background = getDrawable(R.drawable.bg_ball_indicator)
                val runVal = ball.toIntOrNull()
                when {
                    ball == "•" -> { setTextColor(Color.parseColor("#616161")); background?.setTint(Color.parseColor("#E0E0E0")) }
                    ball == "4" -> { setTextColor(Color.WHITE); background?.setTint(Color.parseColor("#1E88E5")) }
                    ball == "6" -> { setTextColor(Color.WHITE); background?.setTint(Color.parseColor("#7B1FA2")) }
                    ball == "W" -> { setTextColor(Color.WHITE); background?.setTint(Color.parseColor("#D32F2F")) }
                    ball == "Wd" -> { setTextColor(Color.BLACK); background?.setTint(Color.parseColor("#FF8F00")) }
                    ball == "Nb" -> { setTextColor(Color.BLACK); background?.setTint(Color.parseColor("#EF6C00")) }
                    runVal != null && runVal > 0 -> { setTextColor(Color.WHITE); background?.setTint(Color.parseColor("#1B5E20")) }
                    else -> { setTextColor(Color.parseColor("#616161")); background?.setTint(Color.parseColor("#E0E0E0")) }
                }
            }
            llThisOver.addView(tv)
        }
        if (currentOverBalls.isEmpty()) {
            llThisOver.addView(TextView(this).apply { text = "New over"; textSize = 14f; setTextColor(Color.parseColor("#9E9E9E")) })
        }
    }

    private suspend fun loadPlayers() {
        val s1 = intent.getSerializableExtra("team1Selected") as? ArrayList<Long>
        val s2 = intent.getSerializableExtra("team2Selected") as? ArrayList<Long>

        val t1 = try {
            withContext(Dispatchers.IO) {
                if (tournamentId > 0) {
                    if (!s1.isNullOrEmpty()) SupabaseHelper.getTournamentPlayersByIds(s1)
                    else SupabaseHelper.getTournamentPlayersByTeamName(tournamentId, team1Name)
                } else {
                    if (!s1.isNullOrEmpty()) SupabaseHelper.getPlayersForTeamByIds(team1Name, s1)
                    else SupabaseHelper.getPlayersForTeam(team1Name)
                }
            }
        } catch (e: Exception) {
            Log.e("ScoreActivity", "Failed to load team1 players: ${e.message}", e)
            emptyList<Player>()
        }
        val t2 = try {
            withContext(Dispatchers.IO) {
                if (tournamentId > 0) {
                    if (!s2.isNullOrEmpty()) SupabaseHelper.getTournamentPlayersByIds(s2)
                    else SupabaseHelper.getTournamentPlayersByTeamName(tournamentId, team2Name)
                } else {
                    if (!s2.isNullOrEmpty()) SupabaseHelper.getPlayersForTeamByIds(team2Name, s2)
                    else SupabaseHelper.getPlayersForTeam(team2Name)
                }
            }
        } catch (e: Exception) {
            Log.e("ScoreActivity", "Failed to load team2 players: ${e.message}", e)
            emptyList<Player>()
        }
        if (t1.isNotEmpty()) {
            team1Players.clear()
            team1Players.addAll(deduplicateNames(t1.map { it.name }))
        }
        if (t2.isNotEmpty()) {
            team2Players.clear()
            team2Players.addAll(deduplicateNames(t2.map { it.name }))
        }
    }

    private fun deduplicateNames(names: List<String>): ArrayList<String> {
        val result = ArrayList<String>()
        val counts = mutableMapOf<String, Int>()
        for (name in names) {
            if (counts.containsKey(name)) {
                val count = counts[name]!! + 1
                counts[name] = count
                result.add("$name ($count)")
            } else {
                counts[name] = 1
                result.add(name)
            }
        }
        return result
    }

    private fun showFullScorecard() {
        val intent = Intent(this, ScorecardActivity::class.java)
        val m = currentMatch ?: return
        
        intent.putExtra("teamName", if (isTeam1Batting) team1Name else team2Name)
        intent.putExtra("totalRuns", if (isTeam1Batting) m.team1Runs else m.team2Runs)
        intent.putExtra("totalWickets", if (isTeam1Batting) m.team1Wickets else m.team2Wickets)
        intent.putExtra("totalBalls", if (isTeam1Batting) m.team1Balls else m.team2Balls)
        intent.putExtra("extras", if (isTeam1Batting) m.team1Extras else m.team2Extras)
        
        val gson = Gson()
        // Update current players flags for display before passing
        striker?.isOnStrike = true
        nonStriker?.isOnStrike = false

        val batsmen = if (isTeam1Batting) allBatsmenInnings1 else allBatsmenInnings2
        val bowlers = if (isTeam1Batting) allBowlersInnings2 else allBowlersInnings1
        
        intent.putExtra("batsmenJson", gson.toJson(batsmen))
        intent.putExtra("bowlersJson", gson.toJson(bowlers))
        startActivity(intent)
    }
}
