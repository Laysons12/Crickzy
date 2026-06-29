package com.example.crickzy.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.database.SupabaseHelper
import com.example.crickzy.models.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import java.util.ArrayList
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import com.example.crickzy.utils.safeSupabaseCall

class TournamentDetailActivity : AppCompatActivity() {

    private var tournamentId: Long = -1
    private val teamNameToId = mutableMapOf<String, Long>()
    private val teamIdToPlayers = mutableMapOf<Long, List<Player>>()

    private lateinit var tvTournamentName: TextView
    private lateinit var tvTournamentInfo: TextView
    private lateinit var tvFormat: TextView
    private lateinit var tvTeamsHeader: TextView
    private lateinit var llTeamsList: LinearLayout
    private lateinit var btnAddTeam: ImageButton
    private lateinit var btnCreateFixtures: Button
    private lateinit var tvFixturesHeader: TextView
    private lateinit var rvFixtures: RecyclerView
    private lateinit var tvPointsTableHeader: TextView
    private lateinit var llPointsTable: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament_detail)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        tournamentId = intent.getLongExtra("tournamentId", -1)

        tvTournamentName = findViewById(R.id.tvTournamentName)
        tvTournamentInfo = findViewById(R.id.tvTournamentInfo)
        tvFormat = findViewById(R.id.tvFormat)
        tvTeamsHeader = findViewById(R.id.tvTeamsHeader)
        llTeamsList = findViewById(R.id.llTeamsList)
        btnAddTeam = findViewById(R.id.btnAddTeam)
        btnCreateFixtures = findViewById(R.id.btnCreateFixtures)
        tvFixturesHeader = findViewById(R.id.tvFixturesHeader)
        rvFixtures = findViewById(R.id.rvFixtures)
        tvPointsTableHeader = findViewById(R.id.tvPointsTableHeader)
        llPointsTable = findViewById(R.id.llPointsTable)

        rvFixtures.layoutManager = LinearLayoutManager(this)

        btnAddTeam.setOnClickListener { showAddTeamDialog() }
        btnCreateFixtures.setOnClickListener { 
            lifecycleScope.launch {
                val tournament = try { withContext(Dispatchers.IO) { SupabaseHelper.getTournamentById(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to get tournament: ${e.message}"); null }
                if (tournament != null) {
                    val teamIds = try { withContext(Dispatchers.IO) { SupabaseHelper.getTournamentTeamIds(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to get team IDs: ${e.message}"); emptyList() }
                    if (teamIds.size < 2) {
                        Toast.makeText(this@TournamentDetailActivity, "Need at least 2 teams to create fixtures", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    
                    val teamNames = teamIds.map { it.second }
                    // Always let the user choose the format via dialog
                    val formats = arrayOf("Knockout", "League")
                    AlertDialog.Builder(this@TournamentDetailActivity)
                        .setTitle("Select Tournament Format")
                        .setItems(formats) { _, which ->
                            val chosenFormat = formats[which]
                            val intent = Intent(this@TournamentDetailActivity, TournamentFixtureActivity::class.java).apply {
                                putExtra("tournamentId", tournamentId)
                                putExtra("tournamentFormat", chosenFormat)
                                putExtra("teamCount", teamNames.size)
                                putStringArrayListExtra("teamNames", ArrayList(teamNames))
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        loadTournamentData()
    }

    private var loadJob: kotlinx.coroutines.Job? = null

    override fun onResume() {
        super.onResume()
        loadTournamentData()
    }

    private fun loadTournamentData() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val tournament = try { withContext(Dispatchers.IO) { SupabaseHelper.getTournamentById(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "loadTournamentData failed: ${e.message}"); null } ?: return@launch

            tvTournamentName.text = tournament.name
            tvTournamentInfo.text = "${tournament.location} • ${tournament.overs} overs • ${tournament.ballType} • ${tournament.startDate} - ${tournament.endDate}"

            if (tournament.format.isNotEmpty()) {
                tvFormat.text = "Format: ${tournament.format}"
                tvFormat.visibility = View.VISIBLE
            } else {
                tvFormat.visibility = View.GONE
            }

            loadTeamsParallel(tournament.fixturesCreated)

            // Show Add Team button only if fixtures not created
            btnAddTeam.visibility = if (tournament.fixturesCreated) View.GONE else View.VISIBLE

            if (tournament.fixturesCreated) {
                btnCreateFixtures.visibility = View.GONE
                tvFixturesHeader.visibility = View.VISIBLE
                rvFixtures.visibility = View.VISIBLE
                findViewById<View>(R.id.llFixturesRoot)?.visibility = View.VISIBLE
                loadFixtures()

                Log.d("TournamentDetail", "Format='${tournament.format}', checking for League points table")
                if (tournament.format.equals("League", ignoreCase = true) || tournament.format.lowercase().contains("league")) {
                    tvPointsTableHeader.visibility = View.VISIBLE
                    llPointsTable.visibility = View.VISIBLE
                    findViewById<View>(R.id.llPointsTableRoot)?.visibility = View.VISIBLE
                    loadPointsTable()
                }
            } else {
                btnCreateFixtures.visibility = View.VISIBLE
                tvFixturesHeader.visibility = View.GONE
                rvFixtures.visibility = View.GONE
                findViewById<View>(R.id.llFixturesRoot)?.visibility = View.GONE
                tvPointsTableHeader.visibility = View.GONE
                llPointsTable.visibility = View.GONE
                findViewById<View>(R.id.llPointsTableRoot)?.visibility = View.GONE
            }
        }
    }

    private suspend fun loadTeamsParallel(fixturesCreated: Boolean) = coroutineScope {
        llTeamsList.removeAllViews()
        val teamIds = withContext(Dispatchers.IO) { SupabaseHelper.getTournamentTeamIds(tournamentId) }
        tvTeamsHeader.text = "Teams (${teamIds.size})"
        
        teamNameToId.clear()
        teamIdToPlayers.clear()
        
        val teamDataList = teamIds.map { (teamId, teamName) ->
            teamNameToId[teamName.trim().lowercase()] = teamId
            async(Dispatchers.IO) {
                val playerCount = SupabaseHelper.getTournamentTeamPlayerCount(teamId)
                val playerDetails = SupabaseHelper.getTournamentTeamPlayerDetails(teamId)
                val playerList = playerDetails.map { (pid, name, role) ->
                    Player(id=pid, name=name, role=role, phone="", matchType="", isWicketKeeper=false, isAvailable=true, skillRating=0, expLevel=0f, availabilityDate="", matchTime="")
                }
                teamIdToPlayers[teamId] = playerList
                Triple(teamId, teamName, Pair(playerCount, playerList))
            }
        }.awaitAll()

        for ((teamId, teamName, data) in teamDataList) {
            val playerCount = data.first
            val players = data.second.map { p ->
                if (p.role.isNotEmpty()) "${p.name} (${p.role})" else p.name
            }

            val teamView = LayoutInflater.from(this@TournamentDetailActivity).inflate(R.layout.item_tournament_team, llTeamsList, false)
            teamView.findViewById<TextView>(R.id.tvTeamItemName).text = teamName
            teamView.findViewById<TextView>(R.id.tvPlayerCount).text = "$playerCount players"
            val tvPlayers = teamView.findViewById<TextView>(R.id.tvPlayersList)
            if (players.isNotEmpty()) { tvPlayers.text = players.joinToString(", "); tvPlayers.visibility = View.VISIBLE }
            else tvPlayers.visibility = View.GONE

            // Edit Players button — always visible
            val btnEditPlayers = teamView.findViewById<Button>(R.id.btnEditPlayers)
            btnEditPlayers.visibility = View.VISIBLE
            btnEditPlayers.setOnClickListener { showEditPlayersDialog(teamId, teamName) }

            val btnShowQR = teamView.findViewById<ImageButton>(R.id.btnShowQR)
            btnShowQR.visibility = View.VISIBLE
            btnShowQR.setOnClickListener { showTeamQRCode(teamId, teamName) }

            llTeamsList.addView(teamView)
        }
    }

    // ====== QR Code Generation ======

    private fun generateQRBitmap(content: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun showTeamQRCode(teamId: Long, teamName: String) {
        val qrContent = "crickzy://join?tournamentId=$tournamentId&teamId=$teamId"
        val qrBitmap = generateQRBitmap(qrContent)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(48, 32, 48, 32)
        }

        val tvLabel = TextView(this).apply {
            text = "📱 Scan to join $teamName"
            textSize = 16f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        layout.addView(tvLabel)

        val imageView = ImageView(this).apply {
            setImageBitmap(qrBitmap)
            val px = (280 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(px, px)
        }
        layout.addView(imageView)

        val tvHint = TextView(this).apply {
            text = "Players can scan this QR code to join the team directly."
            textSize = 13f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        layout.addView(tvHint)

        AlertDialog.Builder(this)
            .setTitle("$teamName — QR Code")
            .setView(layout)
            .setPositiveButton("Done", null)
            .show()
    }

    // ====== Add Team Dialog ======

    private fun showAddTeamDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_team, null)
        val etTeamName = dialogView.findViewById<EditText>(R.id.etTeamName)
        val etPlayersInput = dialogView.findViewById<EditText>(R.id.etPlayersInput)
        dialogView.findViewById<TextView>(R.id.tvPlayerCountHint).text = "Enter player names (one per line). Players can also join via QR code later."

        AlertDialog.Builder(this)
            .setTitle("Register Team")
            .setView(dialogView)
            .setPositiveButton("Register") { dialog, _ ->
                val teamName = etTeamName.text.toString().trim()
                val playersText = etPlayersInput.text.toString().trim()
                if (teamName.isEmpty()) { Toast.makeText(this, "Enter team name", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                
                val playerNames = playersText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (playerNames.size > 30) { Toast.makeText(this, "Maximum 30 players allowed! You entered ${playerNames.size}", Toast.LENGTH_LONG).show(); return@setPositiveButton }

                // Disable button to prevent double registration
                (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false

                lifecycleScope.launch {
                    try {
                        // Check unique team name
                        val taken = withContext(Dispatchers.IO) { SupabaseHelper.isTournamentTeamNameTaken(tournamentId, teamName) }
                        if (taken) {
                            Toast.makeText(this@TournamentDetailActivity, "A team with this name is already registered!", Toast.LENGTH_SHORT).show()
                            (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                            return@launch
                        }

                        val teamId = withContext(Dispatchers.IO) { SupabaseHelper.addTournamentTeam(tournamentId, teamName) }
                        if (teamId > 0) {
                            for (name in playerNames) {
                                withContext(Dispatchers.IO) { SupabaseHelper.addTournamentTeamPlayer(teamId, name) }
                            }
                            
                            val playerMsg = if (playerNames.isNotEmpty()) " with ${playerNames.size} players" else ""
                            Toast.makeText(this@TournamentDetailActivity, "$teamName registered$playerMsg", Toast.LENGTH_SHORT).show()
                            
                            showTeamQRCode(teamId, teamName)
                            loadTournamentData()
                        } else {
                            Toast.makeText(this@TournamentDetailActivity, "Failed to register team", Toast.LENGTH_SHORT).show()
                            (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@TournamentDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ====== Selection Dialogs ======

    private fun showLeagueMatchCountDialog(teamNames: List<String>) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "e.g., 1 or 2"
        input.setText("1")
        input.setPadding(48, 24, 48, 24)
        
        AlertDialog.Builder(this)
            .setTitle("League Match Count")
            .setMessage("How many times should each team play against every other team?")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val count = input.text.toString().toIntOrNull() ?: 1
                lifecycleScope.launch { createFixtures("League", teamNames, count) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditPlayersDialog(teamId: Long, teamName: String) {
        lifecycleScope.launch {
            val playerDetails = try { withContext(Dispatchers.IO) { SupabaseHelper.getTournamentTeamPlayerDetails(teamId) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to get player details: ${e.message}"); emptyList() }

            val container = LinearLayout(this@TournamentDetailActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 24, 48, 24)
            }

            // Rename Team Section
            val btnRenameTeam = Button(this@TournamentDetailActivity).apply {
                text = "🏷️ Rename Team ($teamName)"
                textSize = 13f
                setPadding(24, 16, 24, 16)
                setBackgroundColor(Color.parseColor("#424242"))
                setTextColor(Color.WHITE)
            }
            btnRenameTeam.setOnClickListener {
                val etRename = EditText(this@TournamentDetailActivity).apply { setText(teamName); setPadding(48, 24, 48, 24) }
                AlertDialog.Builder(this@TournamentDetailActivity)
                    .setTitle("Rename Team")
                    .setView(etRename)
                    .setPositiveButton("Save") { _, _ ->
                        val newName = etRename.text.toString().trim()
                        if (newName.isNotEmpty() && newName != teamName) {
                            lifecycleScope.launch {
                                val nameTaken = try {
                                    withContext(Dispatchers.IO) { SupabaseHelper.isTournamentTeamNameTaken(tournamentId, newName) }
                                } catch (e: Exception) {
                                    Log.e("TournamentDetail", "Failed to check team name: ${e.message}")
                                    true // Assume taken on error to be safe
                                }
                                if (!nameTaken) {
                                    try { withContext(Dispatchers.IO) { SupabaseHelper.updateTournamentTeamName(teamId, newName) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to update team name: ${e.message}") }
                                    loadTournamentData()
                                } else {
                                    Toast.makeText(this@TournamentDetailActivity, "Team name already exists!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            container.addView(btnRenameTeam)
            container.addView(View(this@TournamentDetailActivity).apply { layoutParams = LinearLayout.LayoutParams(1, 16) }) // spacing

            // Current players with role tags, rename and remove buttons
            for ((id, playerName, role) in playerDetails) {
                val row = LinearLayout(this@TournamentDetailActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 8, 0, 8)
                }
                
                val displayName = if (role.isNotEmpty()) "$playerName (${role[0]})" else playerName
                val tvName = TextView(this@TournamentDetailActivity).apply {
                    text = displayName
                    textSize = 15f
                    setTextColor(if (role.isNotEmpty()) Color.parseColor("#FFD54F") else Color.parseColor("#E0E0E0"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        val etRename = EditText(this@TournamentDetailActivity).apply { setText(playerName); setPadding(48, 24, 48, 24) }
                        AlertDialog.Builder(this@TournamentDetailActivity)
                            .setTitle("Rename Player")
                            .setView(etRename)
                            .setPositiveButton("Save") { _, _ ->
                                val newName = etRename.text.toString().trim()
                                if (newName.isNotEmpty() && newName != playerName) {
                                    lifecycleScope.launch {
                                        try { withContext(Dispatchers.IO) { SupabaseHelper.updateTournamentTeamPlayerName(id, newName) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to rename player: ${e.message}") }
                                        loadTournamentData()
                                        showEditPlayersDialog(teamId, teamName) 
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }

                val btnRole = Button(this@TournamentDetailActivity).apply {
                    text = "👑"
                    textSize = 12f
                    minWidth = 0; minimumWidth = 0
                    setPadding(16, 0, 16, 0)
                    setBackgroundColor(Color.parseColor("#455A64"))
                    setTextColor(Color.WHITE)
                }
                btnRole.setOnClickListener {
                    val roles = arrayOf("None", "Captain", "Vice-Captain")
                    AlertDialog.Builder(this@TournamentDetailActivity)
                        .setTitle("Set Role for $playerName")
                        .setItems(roles) { _, which ->
                            val selectedRole = if (which == 0) "" else roles[which]
                            lifecycleScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        if (selectedRole.isNotEmpty()) SupabaseHelper.clearTeamRoles(teamId, selectedRole)
                                        SupabaseHelper.updatePlayerRole(id, selectedRole)
                                    }
                                } catch (e: Exception) { Log.e("TournamentDetail", "Failed to update role: ${e.message}") }
                                loadTournamentData()
                                showEditPlayersDialog(teamId, teamName)
                            }
                        }
                        .show()
                }

                val btnRemove = Button(this@TournamentDetailActivity).apply {
                    text = "✕"
                    textSize = 14f
                    minWidth = 0; minimumWidth = 0
                    setPadding(16, 0, 16, 0)
                    setBackgroundColor(Color.parseColor("#D32F2F"))
                    setTextColor(Color.WHITE)
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.marginStart = 8
                    layoutParams = lp
                }
                btnRemove.setOnClickListener {
                    lifecycleScope.launch {
                        try { withContext(Dispatchers.IO) { SupabaseHelper.removeTournamentTeamPlayer(teamId, playerName) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to remove player: ${e.message}") }
                        Toast.makeText(this@TournamentDetailActivity, "$playerName removed", Toast.LENGTH_SHORT).show()
                        loadTournamentData()
                        showEditPlayersDialog(teamId, teamName)
                    }
                }
                
                row.addView(tvName)
                row.addView(btnRole)
                row.addView(btnRemove)
                container.addView(row)
            }

            if (playerDetails.isEmpty()) {
                container.addView(TextView(this@TournamentDetailActivity).apply {
                    text = "No players yet"
                    textSize = 14f
                    setTextColor(Color.parseColor("#9E9E9E"))
                    setPadding(0, 16, 0, 16)
                })
            }

            // Divider
            container.addView(View(this@TournamentDetailActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 16; bottomMargin = 16 }
                setBackgroundColor(Color.parseColor("#333355"))
            })

            // Add player input
            val etNewPlayer = EditText(this@TournamentDetailActivity).apply {
                hint = "Enter player name(s) (one per line)"
                textSize = 15f
                setPadding(24, 16, 24, 16)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setSingleLine(false)
                maxLines = 10
            }
            container.addView(etNewPlayer)

            AlertDialog.Builder(this@TournamentDetailActivity)
                .setTitle("✏️ Edit Players — $teamName")
                .setView(container)
                .setPositiveButton("Add Player") { _, _ ->
                    val input = etNewPlayer.text.toString().trim()
                    if (input.isEmpty()) { Toast.makeText(this@TournamentDetailActivity, "Enter player name(s)", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                    val newNames = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (newNames.isEmpty()) { Toast.makeText(this@TournamentDetailActivity, "Enter player name(s)", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                    
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                for (name in newNames) {
                                    SupabaseHelper.addTournamentTeamPlayer(teamId, name)
                                }
                            }
                            val msg = if (newNames.size == 1) "${newNames.first()} added" else "${newNames.size} players added"
                            Toast.makeText(this@TournamentDetailActivity, msg, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("TournamentDetail", "Failed to add player(s): ${e.message}")
                            Toast.makeText(this@TournamentDetailActivity, "Error adding players", Toast.LENGTH_SHORT).show()
                        }
                        loadTournamentData()
                        showEditPlayersDialog(teamId, teamName)
                    }
                }
                .setNegativeButton("Done") { _, _ -> loadTournamentData() }
                .show()
        }
    }

    // ====== Format Selection & Fixture Creation ======


    private suspend fun createFixtures(format: String, teamNames: List<String>, count: Int) {
        val shuffled = teamNames.toMutableList().apply { shuffle(); shuffle() }
        var matchNum = 1
        Log.d("Fixtures", "Creating $format fixtures for ${shuffled.size} teams: $shuffled, count=$count")

        when (format) {
            "Knockout" -> {
                val matches = mutableListOf<Fixture>()
                var currentMatchNum = 1
                val initialTeams = shuffled.toMutableList()
                val n = initialTeams.size
                
                // 1. Calculate next power of two
                var p = 1
                while (p < n) p *= 2
                
                // 2. Calculate byes
                val numByes = p - n
                val teamsPlayingRound1 = n - numByes
                Log.d("Fixtures", "Knockout: n=$n, nextPow2=$p, byes=$numByes, teamsInRound1=$teamsPlayingRound1")
                
                // 3. Create Round 1 Matches
                val round1Winners = mutableListOf<String>()
                val byeTeams = mutableListOf<String>()
                
                // Teams that play in Round 1
                for (i in 0 until teamsPlayingRound1 step 2) {
                    val f = Fixture(
                        tournamentId = tournamentId,
                        team1Name = initialTeams[i],
                        team2Name = initialTeams[i + 1],
                        matchNumber = currentMatchNum,
                        round = "Round 1"
                    )
                    matches.add(f)
                    round1Winners.add("TBD")
                    currentMatchNum++
                }
                
                // Teams that get byes (start from where Round 1 teams left off)
                for (i in teamsPlayingRound1 until n) {
                    byeTeams.add(initialTeams[i])
                    Toast.makeText(this, "${initialTeams[i]} gets a BYE to the next round!", Toast.LENGTH_SHORT).show()
                }
                
                // 4. Round 2 Teams (Round 1 Winners + Byes)
                var teamsInCurrentRound = round1Winners + byeTeams
                
                // 5. Generate subsequent rounds (Quarter, Semi, Final etc)
                while (teamsInCurrentRound.size > 1) {
                    val roundSize = teamsInCurrentRound.size
                    val roundLabel = when {
                        roundSize <= 2 -> "Final"
                        roundSize <= 4 -> "Semi-Final"
                        roundSize <= 8 -> "Quarter-Final"
                        else -> "Round of $roundSize"
                    }
                    
                    val winnersOfThisRound = mutableListOf<String>()
                    for (i in 0 until teamsInCurrentRound.size step 2) {
                        if (i + 1 >= teamsInCurrentRound.size) break
                        val team1 = teamsInCurrentRound[i]
                        val team2 = teamsInCurrentRound[i + 1]
                        val f = Fixture(
                            tournamentId = tournamentId,
                            team1Name = team1,
                            team2Name = team2,
                            matchNumber = currentMatchNum,
                            round = roundLabel
                        )
                        matches.add(f)
                        winnersOfThisRound.add("TBD")
                        currentMatchNum++
                    }
                    teamsInCurrentRound = winnersOfThisRound
                }

                Log.d("Fixtures", "Knockout: total fixtures to create = ${matches.size}")
                var created = 0
                withContext(Dispatchers.IO) {
                    for (m in matches) {
                        try {
                            SupabaseHelper.addFixture(m)
                            created++
                        } catch (e: Exception) {
                            Log.e("Fixtures", "Failed to add knockout fixture #${m.matchNumber}: ${e.message}")
                        }
                    }
                }
                Log.d("Fixtures", "Knockout: successfully created $created/${matches.size} fixtures")
                matchNum = currentMatchNum
            }
            "League" -> {
                // Round-robin: every team plays every other team, 'count' times
                val leagueFixtures = mutableListOf<Fixture>()
                for (round in 1..count) {
                    for (i in shuffled.indices) {
                        for (j in i + 1 until shuffled.size) {
                            leagueFixtures.add(Fixture(
                                tournamentId = tournamentId,
                                team1Name = shuffled[i],
                                team2Name = shuffled[j],
                                matchNumber = matchNum,
                                round = if (count > 1) "League Rd $round" else "League"
                            ))
                            matchNum++
                        }
                    }
                }

                // Playoff fixtures
                if (shuffled.size >= 4) {
                    leagueFixtures.add(Fixture(tournamentId = tournamentId, team1Name = "TBD", team2Name = "TBD", matchNumber = matchNum, round = "Semi-Final"))
                    matchNum++
                    leagueFixtures.add(Fixture(tournamentId = tournamentId, team1Name = "TBD", team2Name = "TBD", matchNumber = matchNum, round = "Semi-Final"))
                    matchNum++
                    leagueFixtures.add(Fixture(tournamentId = tournamentId, team1Name = "TBD", team2Name = "TBD", matchNumber = matchNum, round = "Final"))
                } else if (shuffled.size >= 2) {
                    leagueFixtures.add(Fixture(tournamentId = tournamentId, team1Name = "TBD", team2Name = "TBD", matchNumber = matchNum, round = "Final"))
                }

                Log.d("Fixtures", "League: total fixtures to create = ${leagueFixtures.size} (${leagueFixtures.count { it.round.contains("League") }} league + ${leagueFixtures.count { !it.round.contains("League") }} playoffs)")
                var created = 0
                withContext(Dispatchers.IO) {
                    for (f in leagueFixtures) {
                        try {
                            SupabaseHelper.addFixture(f)
                            created++
                        } catch (e: Exception) {
                            Log.e("Fixtures", "Failed to add league fixture #${f.matchNumber}: ${e.message}")
                        }
                    }
                }
                Log.d("Fixtures", "League: successfully created $created/${leagueFixtures.size} fixtures")
            }
        }

        try {
            withContext(Dispatchers.IO) {
                val tournament = SupabaseHelper.getTournamentById(tournamentId)
                tournament?.let { it.format = format; it.fixturesCreated = true; SupabaseHelper.updateTournament(it) }
            }
        } catch (e: Exception) { Log.e("TournamentDetail", "Failed to update tournament format: ${e.message}") }
        Toast.makeText(this, "Fixtures created! Format: $format", Toast.LENGTH_LONG).show()
        loadTournamentData()
    }

private suspend fun loadFixtures() {
val fixtures = try { withContext(Dispatchers.IO) { SupabaseHelper.getFixturesForTournament(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to load fixtures: ${e.message}"); emptyList() }
rvFixtures.adapter = FixtureListAdapter(fixtures)
}

    private suspend fun getPlayersForTeam(teamName: String): ArrayList<String> {
        val teamIds = try { withContext(Dispatchers.IO) { SupabaseHelper.getTournamentTeamIds(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "getPlayersForTeam failed: ${e.message}"); emptyList() }
        val teamId = teamIds.find { it.second == teamName }?.first ?: return arrayListOf()
        return ArrayList(withContext(Dispatchers.IO) { SupabaseHelper.getTournamentTeamPlayers(teamId) })
    }

    private suspend fun launchScoreActivity(fixture: Fixture, matchId: Long, s1: List<Long>? = null, s2: List<Long>? = null, isNewMatch: Boolean = false) {
        val tournament = try { withContext(Dispatchers.IO) { SupabaseHelper.getTournamentById(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "launchScoreActivity failed to get tournament: ${e.message}"); null }
        val overs = tournament?.overs ?: 20

        val team1Players = if (s1 != null) {
            ArrayList(withContext(Dispatchers.IO) { SupabaseHelper.getTournamentPlayersByIds(s1) }.map { it.name })
        } else getPlayersForTeam(fixture.team1Name)

        val team2Players = if (s2 != null) {
            ArrayList(withContext(Dispatchers.IO) { SupabaseHelper.getTournamentPlayersByIds(s2) }.map { it.name })
        } else getPlayersForTeam(fixture.team2Name)

        val intent = Intent(this, ScoreActivity::class.java)
        intent.putExtra("matchId", matchId)
        intent.putExtra("team1Name", fixture.team1Name)
        intent.putExtra("team2Name", fixture.team2Name)
        intent.putExtra("tournamentId", tournamentId)
        intent.putExtra("fixtureId", fixture.id)
        intent.putExtra("totalOvers", overs)
        intent.putExtra("isNewMatch", isNewMatch)
        intent.putStringArrayListExtra("team1Players", team1Players)
        intent.putStringArrayListExtra("team2Players", team2Players)
        if (s1 != null) intent.putExtra("team1Selected", ArrayList(s1))
        if (s2 != null) intent.putExtra("team2Selected", ArrayList(s2))
        startActivity(intent)
    }

    private suspend fun loadPointsTable() {
        llPointsTable.removeAllViews()
        val teams = try { withContext(Dispatchers.IO) { SupabaseHelper.getTournamentTeams(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "loadPointsTable teams failed: ${e.message}"); emptyList() }
        val matches = try { withContext(Dispatchers.IO) { SupabaseHelper.getMatchesForTournament(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "loadPointsTable matches failed: ${e.message}"); emptyList() }
        val tournament = try { withContext(Dispatchers.IO) { SupabaseHelper.getTournamentById(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "loadPointsTable tournament failed: ${e.message}"); null }

        Log.d("PointsTable", "=== POINTS TABLE CALCULATION ===")
        Log.d("PointsTable", "Tournament ID: $tournamentId, Format: ${tournament?.format}")
        Log.d("PointsTable", "Teams (${teams.size}): $teams")
        for (m in matches) {
            Log.d("PointsTable", "Match id=${m.id}: '${m.team1Name}' (${m.team1Runs}/${m.team1Wickets}) vs '${m.team2Name}' (${m.team2Runs}/${m.team2Wickets}), status='${m.matchStatus}', winner='${m.winner}', tid=${m.tournamentId}")
        }

        if (teams.isEmpty()) {
            Log.w("PointsTable", "No teams found for tournament $tournamentId")
            return
        }

        val entries = teams.map { teamName ->
            val entry = PointsTableEntry(teamName = teamName)
            val teamId = teamNameToId[teamName.trim().lowercase()] ?: -1L
            val teamSize = teamIdToPlayers[teamId]?.size ?: 11
            val allOutWickets = if (teamSize > 0) teamSize - 1 else 10

            for (m in matches) {
                val status = m.matchStatus.lowercase().trim()
                val isFinished = status == "finished" || status == "completed"
                val isAbandoned = status.contains("abandoned") || status.contains("no result")
                
                if (!isFinished && !isAbandoned) {
                    Log.d("PointsTable", "  Skipping match ${m.id} for team '$teamName': status='$status' not finished/abandoned")
                    continue
                }

                // Case-insensitive and trimmed team name comparison
                val t1Match = m.team1Name.trim().equals(teamName.trim(), ignoreCase = true)
                val t2Match = m.team2Name.trim().equals(teamName.trim(), ignoreCase = true)
                val matchesTeam = t1Match || t2Match

                if (!matchesTeam) continue

                entry.played++
                    
                if (isAbandoned) {
                    entry.tied++
                    entry.points += 1
                    Log.d("PointsTable", "  Match ${m.id}: '$teamName' -> ABANDONED (+1pt)")
                } else {
                    // Determine winner using multiple strategies for robustness
                    val winnerStr = m.winner.trim()
                    val isTied = winnerStr.contains("Tied", ignoreCase = true) || winnerStr.contains("Draw", ignoreCase = true)
                    
                    // Strategy 1: winner string starts with team name (most reliable)
                    val wonByWinnerStr = winnerStr.startsWith(teamName.trim(), ignoreCase = true)
                    
                    // Strategy 2: Determine winner from scores (fallback)
                    val isTeam1InMatch = t1Match
                    val myRuns = if (isTeam1InMatch) m.team1Runs else m.team2Runs
                    val oppRuns = if (isTeam1InMatch) m.team2Runs else m.team1Runs
                    val wonByScores = myRuns > oppRuns
                    
                    // Use winner string if available, fallback to scores
                    val teamWon = if (winnerStr.isNotEmpty() && !isTied) wonByWinnerStr else wonByScores
                    
                    if (isTied && m.team1Runs == m.team2Runs) {
                        entry.tied++
                        entry.points += 1
                        Log.d("PointsTable", "  Match ${m.id}: '$teamName' -> TIED (+1pt)")
                    } else if (teamWon) {
                        entry.won++
                        entry.points += 2
                        Log.d("PointsTable", "  Match ${m.id}: '$teamName' -> WON (+2pts), winner='$winnerStr', byStr=$wonByWinnerStr, byScore=$wonByScores")
                    } else {
                        entry.lost++
                        Log.d("PointsTable", "  Match ${m.id}: '$teamName' -> LOST (+0pts), winner='$winnerStr', byStr=$wonByWinnerStr, byScore=$wonByScores")
                    }
                }

                // NRR: Only for finished matches
                if (isFinished) {
                    val isTeam1 = t1Match
                    val maxBalls = (tournament?.overs ?: 20) * 6
                    
                    // NRR: Batting Stats
                    val runsScored = if (isTeam1) m.team1Runs else m.team2Runs
                    val wicketsLost = if (isTeam1) m.team1Wickets else m.team2Wickets
                    // All Out Rule: If all out, count full allotted overs.
                    val ballsFaced = if (wicketsLost >= allOutWickets) {
                        maxBalls 
                    } else {
                        if (isTeam1) m.team1Balls else m.team2Balls
                    }
                    
                    // NRR: Bowling Stats
                    val runsConceded = if (isTeam1) m.team2Runs else m.team1Runs
                    val wicketsTaken = if (isTeam1) m.team2Wickets else m.team1Wickets
                    
                    val opponentName = if (isTeam1) m.team2Name else m.team1Name
                    val opponentId = teamNameToId[opponentName.trim().lowercase()] ?: -1L
                    val opponentSize = teamIdToPlayers[opponentId]?.size ?: 11
                    val opponentAllOutWickets = if (opponentSize > 0) opponentSize - 1 else 10

                    // Opponent All Out Rule: If opponent all out, count full allotted overs
                    val ballsBowled = if (wicketsTaken >= opponentAllOutWickets) {
                        maxBalls
                    } else {
                        if (isTeam1) m.team2Balls else m.team1Balls
                    }

                    entry.totalRunsScored += runsScored
                    entry.totalBallsFaced += ballsFaced
                    entry.totalRunsConceded += runsConceded
                    entry.totalBallsBowled += ballsBowled
                }
            }
            val oversScored = if (entry.totalBallsFaced > 0) entry.totalBallsFaced / 6.0 else 0.0
            val oversConceded = if (entry.totalBallsBowled > 0) entry.totalBallsBowled / 6.0 else 0.0
            val rs = if (oversScored > 0) entry.totalRunsScored / oversScored else 0.0
            val rc = if (oversConceded > 0) entry.totalRunsConceded / oversConceded else 0.0
            entry.nrr = rs - rc
            Log.d("PointsTable", "RESULT: ${entry.teamName} P=${entry.played} W=${entry.won} L=${entry.lost} T=${entry.tied} Pts=${entry.points} NRR=${"%.3f".format(entry.nrr)}")
            entry
        }.sortedWith(compareByDescending<PointsTableEntry> { it.points }.thenByDescending { it.nrr })

        addPointsTableRow(llPointsTable, "Team", "P", "W", "L", "T", "Pts", "NRR", isHeader = true)
        for (e in entries) {
            val nrrStr = if (e.nrr > 1e-9) "+${String.format("%.3f", e.nrr)}" else if (e.nrr < -1e-9) String.format("%.3f", e.nrr) else "0.000"
            addPointsTableRow(llPointsTable, e.teamName, "${e.played}", "${e.won}", "${e.lost}", "${e.tied}", "${e.points}", nrrStr, isHeader = false)
        }

        checkAndFillLeaguePlayoffs(entries)
    }

    private suspend fun checkAndFillLeaguePlayoffs(entries: List<PointsTableEntry>) {
        val fixtures = try { withContext(Dispatchers.IO) { SupabaseHelper.getFixturesForTournament(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "checkAndFillLeaguePlayoffs failed: ${e.message}"); emptyList() }
        val leagueFixtures = fixtures.filter { it.round.contains("League") }
        if (leagueFixtures.isEmpty() || leagueFixtures.any { it.status != "Completed" }) return

        if (entries.size >= 4) {
            val sfFixtures = fixtures.filter { it.round == "Semi-Final" && it.team1Name == "TBD" }
            if (sfFixtures.size == 2) {
                sfFixtures[0].team1Name = entries[0].teamName
                sfFixtures[0].team2Name = entries[3].teamName
                try { withContext(Dispatchers.IO) { SupabaseHelper.updateFixture(sfFixtures[0]) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to update SF1: ${e.message}") }
                
                sfFixtures[1].team1Name = entries[1].teamName
                sfFixtures[1].team2Name = entries[2].teamName
                try { withContext(Dispatchers.IO) { SupabaseHelper.updateFixture(sfFixtures[1]) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to update SF2: ${e.message}") }
                
                Toast.makeText(this, "Semi-Final matchups set!", Toast.LENGTH_SHORT).show()
                loadFixtures()
            }
        } else if (entries.size in 2..3) {
            val finalFixture = fixtures.find { it.round == "Final" && it.team1Name == "TBD" }
            if (finalFixture != null) {
                finalFixture.team1Name = entries[0].teamName
                finalFixture.team2Name = entries[1].teamName
                try { withContext(Dispatchers.IO) { SupabaseHelper.updateFixture(finalFixture) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to update Final: ${e.message}") }
                
                Toast.makeText(this, "Final matchup set!", Toast.LENGTH_SHORT).show()
                loadFixtures()
            }
        }
    }

    private fun addPointsTableRow(parent: LinearLayout, team: String, p: String, w: String, l: String, t: String, pts: String, nrr: String, isHeader: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(8, 12, 8, 12)
            if (isHeader) setBackgroundColor(Color.parseColor("#1A1A2E"))
        }
        val style = if (isHeader) Typeface.BOLD else Typeface.NORMAL
        val color = if (isHeader) Color.parseColor("#80CBC4") else Color.parseColor("#E0E0E0")
        row.addView(createPTTextView(team, 2.0f, style, color))
        row.addView(createPTTextView(p, 0.8f, style, color))
        row.addView(createPTTextView(w, 0.8f, style, Color.parseColor(if (isHeader) "#80CBC4" else "#66BB6A")))
        row.addView(createPTTextView(l, 0.8f, style, Color.parseColor(if (isHeader) "#80CBC4" else "#EF5350")))
        row.addView(createPTTextView(t, 0.8f, style, Color.parseColor(if (isHeader) "#80CBC4" else "#B0BEC5")))
        row.addView(createPTTextView(pts, 0.8f, Typeface.BOLD, Color.parseColor(if (isHeader) "#80CBC4" else "#FFD54F")))
        row.addView(createPTTextView(nrr, 1.5f, style, color))
        parent.addView(row)
        parent.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1); setBackgroundColor(Color.parseColor("#333355")) })
    }

    private fun createPTTextView(text: String, weight: Float, style: Int, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            setTypeface(null, style); setTextColor(color); textSize = 13f
            if (weight == 1f) gravity = android.view.Gravity.CENTER
        }
    }

    // ====== Inner Fixture Adapter ======
    inner class FixtureListAdapter(private val fixtures: List<Fixture>) :
        RecyclerView.Adapter<FixtureListAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvMatchNum: TextView = view.findViewById(R.id.tvMatchNum)
            val tvRound: TextView = view.findViewById(R.id.tvRound)
            val tvTeam1: TextView = view.findViewById(R.id.tvFixTeam1)
            val tvTeam2: TextView = view.findViewById(R.id.tvFixTeam2)
            val tvVs: TextView = view.findViewById(R.id.tvVs)
            val tvStatus: TextView = view.findViewById(R.id.tvFixStatus)
            val btnStartMatch: Button = view.findViewById(R.id.btnStartMatch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_fixture, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val fixture = fixtures[position]
            holder.tvMatchNum.text = "Match ${fixture.matchNumber}"
            holder.tvRound.text = fixture.round
            holder.tvTeam1.text = fixture.team1Name
            holder.tvTeam2.text = fixture.team2Name

            val roundColor = when {
                fixture.round == "Final" -> "#FFD54F"
                fixture.round == "Semi-Final" -> "#80CBC4"
                fixture.round == "Quarter-Final" -> "#81C784"
                fixture.round.contains("Eliminator") -> "#FF8A65"
                else -> "#80CBC4"
            }
            holder.tvRound.setTextColor(Color.parseColor(roundColor))

            if (fixture.team1Name == "TBD" || fixture.team2Name == "TBD") {
                holder.tvStatus.text = "⏳ Waiting for results"
                holder.tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                holder.btnStartMatch.visibility = View.GONE
                holder.tvTeam1.setTextColor(Color.parseColor("#757575"))
                holder.tvTeam2.setTextColor(Color.parseColor("#757575"))
                return
            }

            holder.tvTeam1.setTextColor(Color.WHITE)
            holder.tvTeam2.setTextColor(Color.WHITE)

            when (fixture.status) {
                "Upcoming" -> {
                    holder.tvStatus.text = "Upcoming"; holder.tvStatus.setTextColor(Color.parseColor("#FF8F00"))
                    holder.btnStartMatch.visibility = View.VISIBLE; holder.btnStartMatch.text = "Start Match"
                }
                "Live" -> {
                    holder.tvStatus.text = "● Live"; holder.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                    holder.btnStartMatch.visibility = View.VISIBLE; holder.btnStartMatch.text = "Continue"
                }
                "Completed" -> {
                    val job = lifecycleScope.launch {
                        val match = if (fixture.matchId > 0) {
                            try { withContext(Dispatchers.IO) { SupabaseHelper.getMatchById(fixture.matchId) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to get match: ${e.message}"); null }
                        } else null
                        if (isActive) holder.tvStatus.text = match?.winner ?: "Completed"
                    }
                    holder.itemView.setTag(R.id.tvFixStatus, job)
                    holder.tvStatus.setTextColor(Color.parseColor("#42A5F5"))
                    holder.btnStartMatch.visibility = View.GONE
                }
            }

            holder.btnStartMatch.setOnClickListener {
                startMatchFlow(fixture)
            }
            holder.itemView.setOnClickListener {
                if (fixture.team1Name != "TBD" && fixture.team2Name != "TBD") {
                    startMatchFlow(fixture)
                }
            }
        }

        private fun startMatchFlow(fixture: Fixture) {
            lifecycleScope.launch {
                if (fixture.status == "Upcoming") {
                    val id1 = teamNameToId[fixture.team1Name.trim().lowercase()] ?: -1L
                    val id2 = teamNameToId[fixture.team2Name.trim().lowercase()] ?: -1L

                    val p1 = if (id1 != -1L) teamIdToPlayers[id1] ?: emptyList() else emptyList()
                    val p2 = if (id2 != -1L) teamIdToPlayers[id2] ?: emptyList() else emptyList()

                    if (p1.isEmpty() || p2.isEmpty()) {
                        val p1Size = p1.size
                        val p2Size = p2.size
                        val availableNames = teamNameToId.keys.joinToString(",") { "[$it]" }
                        
                        val errorMsg = "Match Start Fail (Cache)!\n" +
                            "T1: '${fixture.team1Name}' (ID: $id1, P: $p1Size)\n" +
                            "T2: '${fixture.team2Name}' (ID: $id2, P: $p2Size)\n" +
                            "Cached Teams: $availableNames\n" +
                            "TID: $tournamentId\n\n" +
                            "Tip: Refresh the screen if teams were just added."
                        
                        AlertDialog.Builder(this@TournamentDetailActivity)
                            .setTitle("Player Cache Missing")
                            .setMessage(errorMsg)
                            .setPositiveButton("OK", null)
                            .show()
                        return@launch
                    }

                    // Playing XI selection for Team 1
                    val selectedP1 = if (p1.size > 11) {
                        selectXI(fixture.team1Name, p1) ?: return@launch
                    } else p1

                    // Playing XI selection for Team 2
                    val selectedP2 = if (p2.size > 11) {
                        selectXI(fixture.team2Name, p2) ?: return@launch
                    } else p2

                    val match = Match(
                        team1Name = fixture.team1Name,
                        team2Name = fixture.team2Name,
                        matchStatus = "Ongoing",
                        tournamentId = tournamentId,
                        fixtureId = fixture.id
                    )
                    
                    val matchId = try { withContext(Dispatchers.IO) { SupabaseHelper.addMatch(match) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to add match: ${e.message}"); -1L }
                    if (matchId > 0) {
                        try { withContext(Dispatchers.IO) { SupabaseHelper.updateFixtureMatchId(fixture.id, matchId, "Live") } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to update fixture: ${e.message}") }
                        try {
                            val prefs = getSharedPreferences("match_state_$matchId", MODE_PRIVATE)
                            prefs.edit().clear().apply()
                        } catch (e: Exception) {
                            Log.e("TournamentDetail", "Failed to clear stale cache: ${e.message}")
                        }
                        launchScoreActivity(fixture, matchId, selectedP1.map { it.id }, selectedP2.map { it.id }, isNewMatch = true)
                    } else {
                        val error = SupabaseHelper.lastError
                        AlertDialog.Builder(this@TournamentDetailActivity)
                            .setTitle("Failed to start match")
                            .setMessage("Reason: $error\n\nPlease check your internet and try again.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } else if (fixture.status == "Live") {
                    var matchId = fixture.matchId
                    if (matchId <= 0) {
                        val matches = try { withContext(Dispatchers.IO) { SupabaseHelper.getMatchesForTournament(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to get matches: ${e.message}"); emptyList() }
                        matchId = matches.find { it.fixtureId == fixture.id }?.id ?: -1
                    }
                    if (matchId > 0) {
                        launchScoreActivity(fixture, matchId)
                    } else {
                        Toast.makeText(this@TournamentDetailActivity, "Match data not found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        private suspend fun selectXI(teamName: String, players: List<Player>): List<Player>? = suspendCancellableCoroutine { cont ->
            val playerNames = players.map { it.name }.toTypedArray()
            val selectedStates = BooleanArray(players.size) { false }
            val selectedIndices = mutableListOf<Int>()

            AlertDialog.Builder(this@TournamentDetailActivity)
                .setTitle("Select Playing XI for $teamName")
                .setMultiChoiceItems(playerNames, selectedStates) { _, index, isChecked ->
                    if (isChecked) {
                        if (selectedIndices.size >= 11) {
                            Toast.makeText(this@TournamentDetailActivity, "Only 11 players allowed!", Toast.LENGTH_SHORT).show()
                            selectedStates[index] = false
                            // Note: The UI might not update automatically for this specific checkbox without re-showing or manual hack
                        } else {
                            selectedIndices.add(index)
                        }
                    } else {
                        selectedIndices.remove(index)
                    }
                }
                .setPositiveButton("Confirm") { _, _ ->
                    if (selectedIndices.size != 11 && players.size >= 11) {
                        Toast.makeText(this@TournamentDetailActivity, "You must select exactly 11 players!", Toast.LENGTH_LONG).show()
                        cont.resume(null)
                    } else {
                        cont.resume(selectedIndices.map { players[it] })
                    }
                }
                .setNegativeButton("Cancel") { _, _ -> cont.resume(null) }
                .setOnCancelListener { cont.resume(null) }
                .show()
        }

        override fun onViewRecycled(holder: VH) {
            super.onViewRecycled(holder)
            (holder.itemView.getTag(R.id.tvFixStatus) as? Job)?.cancel()
        }

        override fun getItemCount(): Int = fixtures.size
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menu?.add(0, 100, 0, "Delete Tournament")?.apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        }
        menu?.add(0, 101, 1, "Regenerate Fixtures")?.apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == 100) {
            AlertDialog.Builder(this)
                .setTitle("Delete Tournament")
                .setMessage("Are you sure you want to delete this tournament? This will delete all associated teams, fixtures, and matches.")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        try { withContext(Dispatchers.IO) { SupabaseHelper.deleteTournament(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to delete tournament: ${e.message}") }
                        Toast.makeText(this@TournamentDetailActivity, "Tournament deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return true
        }
        if (item.itemId == 101) {
            AlertDialog.Builder(this)
                .setTitle("Regenerate Fixtures")
                .setMessage("This will delete all existing fixtures and matches for this tournament. You can then create new fixtures. Continue?")
                .setPositiveButton("Yes") { _, _ ->
                    lifecycleScope.launch {
                        try { withContext(Dispatchers.IO) { SupabaseHelper.resetTournamentFixtures(tournamentId) } } catch (e: Exception) { Log.e("TournamentDetail", "Failed to reset fixtures: ${e.message}") }
                        Toast.makeText(this@TournamentDetailActivity, "Fixtures reset! You can now create new fixtures.", Toast.LENGTH_SHORT).show()
                        loadTournamentData()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
