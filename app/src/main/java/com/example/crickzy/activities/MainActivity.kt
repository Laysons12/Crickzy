package com.example.crickzy.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.crickzy.R
import com.example.crickzy.database.SupabaseHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNavigation)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        val navController = navHostFragment.navController

        // Setup bottom navigation with nav controller
        bottomNav.setupWithNavController(navController)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val appLinkData: Uri? = intent?.data
        if (appLinkData != null && appLinkData.scheme == "crickzy" && appLinkData.host == "join") {
            val tournamentId = appLinkData.getQueryParameter("tournamentId")?.toLongOrNull() ?: -1L
            val teamId = appLinkData.getQueryParameter("teamId")?.toLongOrNull() ?: -1L

            if (tournamentId != -1L && teamId != -1L) {
                showJoinConfirmationDialog(tournamentId, teamId)
            }
        }
    }

    private fun showJoinConfirmationDialog(tournamentId: Long, teamId: Long) {
        lifecycleScope.launch {
            try {
                val tournament = withContext(Dispatchers.IO) { SupabaseHelper.getTournamentById(tournamentId) }
                val teamIds = withContext(Dispatchers.IO) { SupabaseHelper.getTournamentTeamIds(tournamentId) }
                val teamName = teamIds.find { it.first == teamId }?.second ?: "Team"

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Join Team")
                    .setMessage("Do you want to join $teamName in ${tournament?.name ?: "this tournament"}?")
                    .setPositiveButton("Join") { _, _ ->
                        val prefs = getSharedPreferences("CrickzyPrefs", MODE_PRIVATE)
                        val playerName = prefs.getString("loggedInName", "") ?: "New Player"

                        lifecycleScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    SupabaseHelper.addTournamentTeamPlayer(teamId, playerName)
                                }
                                if (result != -1L) {
                                    Toast.makeText(this@MainActivity, "Successfully joined $teamName!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "Failed to join team. Try again.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Join team failed: ${e.message}", e)
                                Toast.makeText(this@MainActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Load tournament failed: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Failed to load tournament info.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
