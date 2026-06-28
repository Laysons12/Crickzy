package com.example.crickzy.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.activities.AddPlayerActivity
import com.example.crickzy.activities.AddTeamActivity
import com.example.crickzy.adapters.PlayerAdapter
import com.example.crickzy.adapters.TeamAdapter
import com.example.crickzy.database.SupabaseHelper
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {

    private lateinit var rvFeaturedPlayers: RecyclerView
    private lateinit var rvRecentTeams: RecyclerView
    private lateinit var rvOngoingMatches: RecyclerView
    private lateinit var llOngoingSection: android.widget.LinearLayout
    private lateinit var fabAddPlayer: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var fabAddTeam: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var rvRecentFixtures: RecyclerView
    private lateinit var llRecentFixturesSection: android.widget.LinearLayout

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedUri = result.contents
            if (scannedUri.startsWith("crickzy://join")) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scannedUri))
                startActivity(intent)
            } else {
                Toast.makeText(context, "Invalid QR code for Crickzy", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        rvFeaturedPlayers = view.findViewById(R.id.rvFeaturedPlayers)
        rvRecentTeams = view.findViewById(R.id.rvRecentTeams)
        rvOngoingMatches = view.findViewById(R.id.rvOngoingMatches)
        llOngoingSection = view.findViewById(R.id.llOngoingSection)
        rvRecentFixtures = view.findViewById(R.id.rvRecentFixtures)
        llRecentFixturesSection = view.findViewById(R.id.llRecentFixturesSection)

        // Setup RecyclerViews
        rvFeaturedPlayers.layoutManager = LinearLayoutManager(context)
        rvRecentTeams.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvOngoingMatches.layoutManager = LinearLayoutManager(context)
        rvRecentFixtures.layoutManager = LinearLayoutManager(context)

        val ivScanQR = view.findViewById<android.widget.ImageButton>(R.id.ivScanQR)
        if (ivScanQR != null) {
            ivScanQR.setOnClickListener {
                val options = ScanOptions()
                options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                options.setPrompt("Scan a Crickzy QR Code")
                options.setCameraId(0)
                options.setBeepEnabled(false)
                options.setBarcodeImageEnabled(true)
                barcodeLauncher.launch(options)
            }
        }

        val tvSeeAllPlayers = view.findViewById<TextView>(R.id.tvSeeAllPlayers)
        if (tvSeeAllPlayers != null) {
            tvSeeAllPlayers.setOnClickListener {
                requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.nav_find_players
            }
        }

        val tvSeeAllTeams = view.findViewById<TextView>(R.id.tvSeeAllTeams)
        if (tvSeeAllTeams != null) {
            tvSeeAllTeams.setOnClickListener {
                requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.nav_find_teams
            }
        }

        fabAddPlayer = view.findViewById(R.id.fabAddPlayer)
        fabAddTeam = view.findViewById(R.id.fabAddTeam)

        setupFloatingActionButtons()

        val ivSettings = view.findViewById<android.widget.ImageView>(R.id.ivSettings)
        ivSettings.setOnClickListener {
            findNavController().navigate(R.id.nav_settings)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val prefs = requireContext().getSharedPreferences("CrickzyPrefs", android.content.Context.MODE_PRIVATE)
        val currentUserId = prefs.getLong("USER_ID", -1L)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val players = withContext(Dispatchers.IO) { SupabaseHelper.getAllPlayers() }
                val teams = withContext(Dispatchers.IO) { SupabaseHelper.getAllTeams() }
                
                val matches = withContext(Dispatchers.IO) { SupabaseHelper.getAllMatches() }
                val ongoingMatches = matches.filter { it.matchStatus == "Ongoing" }
                if (ongoingMatches.isEmpty()) {
                    llOngoingSection.visibility = View.GONE
                } else {
                    llOngoingSection.visibility = View.VISIBLE
                    rvOngoingMatches.adapter = com.example.crickzy.adapters.MatchAdapter(ongoingMatches)
                }

                rvFeaturedPlayers.adapter = PlayerAdapter(players, currentUserId) { player ->
                    showDeletePlayerDialog(player)
                }
                rvRecentTeams.adapter = TeamAdapter(teams, currentUserId) { team ->
                    showDeleteTeamDialog(team)
                }

                val fixtures = withContext(Dispatchers.IO) { SupabaseHelper.getRecentFixtures(5) }
                if (fixtures.isEmpty()) {
                    llRecentFixturesSection.visibility = View.GONE
                } else {
                    llRecentFixturesSection.visibility = View.VISIBLE
                    rvRecentFixtures.adapter = com.example.crickzy.adapters.RecentFixtureAdapter(fixtures)
                }
            } catch (e: Exception) {
                // Ignore or show a toast
                android.util.Log.e("HomeFragment", "Error loading data: ${e.message}")
            }
        }
    }

    private fun showDeletePlayerDialog(player: com.example.crickzy.models.Player) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Player Profile")
            .setMessage("Are you sure you want to delete this player profile?")
            .setPositiveButton("Yes") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { SupabaseHelper.deletePlayer(player.id) }
                    loadData()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showDeleteTeamDialog(team: com.example.crickzy.models.Team) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Team")
            .setMessage("Are you sure you want to delete this team?")
            .setPositiveButton("Yes") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { SupabaseHelper.deleteTeam(team.id) }
                    loadData()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun setupFloatingActionButtons() {
        fabAddPlayer.setOnClickListener {
            startActivity(Intent(requireContext(), AddPlayerActivity::class.java))
        }

        fabAddTeam.setOnClickListener {
            startActivity(Intent(requireContext(), AddTeamActivity::class.java))
        }
    }
}
