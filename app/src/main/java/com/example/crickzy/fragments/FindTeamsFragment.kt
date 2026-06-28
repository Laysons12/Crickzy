package com.example.crickzy.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.activities.AddTeamActivity
import com.example.crickzy.adapters.TeamAdapter
import com.example.crickzy.database.SupabaseHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FindTeamsFragment : Fragment() {

    private lateinit var rvTeams: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var fabAddTeam: FloatingActionButton
    private lateinit var etFilterArea: android.widget.EditText
    private lateinit var btnApplyFilters: android.widget.Button
    private lateinit var chipTeamAll: TextView
    private lateinit var chipTennis: TextView
    private lateinit var chipLeather: TextView
    private var selectedBallType: String = "All"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_find_teams, container, false)
        
        rvTeams = view.findViewById(R.id.rvTeams)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        fabAddTeam = view.findViewById(R.id.fabAddTeam)
        etFilterArea = view.findViewById(R.id.etFilterTeamArea)
        btnApplyFilters = view.findViewById(R.id.btnApplyTeamFilters)
        chipTeamAll = view.findViewById(R.id.chipTeamAll)
        chipTennis = view.findViewById(R.id.chipTennis)
        chipLeather = view.findViewById(R.id.chipLeather)

        rvTeams.layoutManager = LinearLayoutManager(context)
        
        btnApplyFilters.setOnClickListener {
            loadTeams()
        }

        setupFilters()

        fabAddTeam.setOnClickListener {
            startActivity(Intent(requireContext(), AddTeamActivity::class.java))
        }

        // Initialize with default data
        loadTeams()

        return view
    }

    private fun setupFilters() {
        val chips = listOf(chipTeamAll, chipTennis, chipLeather)
        
        chips.forEach { chip ->
            chip.setOnClickListener {
                val chipText = (it as TextView).text.toString()
                selectedBallType = when {
                    chipText.contains("All") -> "All"
                    chipText.contains("Tennis") -> "Tennis Ball" // Matches SupabaseHelper filter logic if needed, or just "Tennis"
                    chipText.contains("Leather") -> "Leather Ball"
                    else -> "All"
                }
                
                // Matches the ball_type values in SupabaseHelper/DB
                if (selectedBallType == "Tennis Ball") selectedBallType = "Tennis"
                if (selectedBallType == "Leather Ball") selectedBallType = "Leather"

                // Update UI: selected vs unselected
                chips.forEach { c ->
                    if (c == it) {
                        c.setBackgroundResource(R.drawable.bg_chip_selected)
                        c.setTextColor(resources.getColor(R.color.white, null))
                    } else {
                        c.setBackgroundResource(R.drawable.bg_chip_unselected)
                        c.setTextColor(resources.getColor(R.color.text_secondary, null))
                    }
                }
                
                loadTeams()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTeams()
    }

    private fun loadTeams() {
        val area = etFilterArea.text.toString().trim()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val teams = withContext(Dispatchers.IO) {
                    SupabaseHelper.getFilteredTeams(selectedBallType, area)
                }

                if (teams.isEmpty()) {
                    tvEmptyState.text = "No teams found for these filters."
                    tvEmptyState.visibility = View.VISIBLE
                    rvTeams.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    rvTeams.visibility = View.VISIBLE
                    val prefs = requireContext().getSharedPreferences("CrickzyPrefs", android.content.Context.MODE_PRIVATE)
                    val currentUserId = prefs.getLong("USER_ID", -1L)
                    
                    rvTeams.adapter = TeamAdapter(teams, currentUserId) { team ->
                        showDeleteTeamDialog(team)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FindTeams", "Error loading teams", e)
                tvEmptyState.text = "Failed to load teams."
                tvEmptyState.visibility = View.VISIBLE
                rvTeams.visibility = View.GONE
            }
        }
    }

    private fun showDeleteTeamDialog(team: com.example.crickzy.models.Team) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Team")
            .setMessage("Are you sure you want to delete this team?")
            .setPositiveButton("Yes") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { SupabaseHelper.deleteTeam(team.id) }
                    loadTeams()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }
}
