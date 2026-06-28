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
import com.example.crickzy.activities.AddPlayerActivity
import com.example.crickzy.adapters.PlayerAdapter
import com.example.crickzy.database.SupabaseHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FindPlayersFragment : Fragment() {

    private lateinit var rvPlayers: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var fabAddPlayer: FloatingActionButton
    private lateinit var etFilterArea: android.widget.EditText
    private lateinit var btnApplyFilters: android.widget.Button
    private lateinit var chipAll: TextView
    private lateinit var chipBatsman: TextView
    private lateinit var chipBowler: TextView
    private lateinit var chipAllRounder: TextView
    private lateinit var chipWicketKeeper: TextView
    private var selectedRole: String = "All"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_find_players, container, false)
        
        rvPlayers = view.findViewById(R.id.rvPlayers)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        fabAddPlayer = view.findViewById(R.id.fabAddPlayer)
        etFilterArea = view.findViewById(R.id.etFilterArea)
        btnApplyFilters = view.findViewById(R.id.btnApplyFilters)
        chipAll = view.findViewById(R.id.chipAll)
        chipBatsman = view.findViewById(R.id.chipBatsman)
        chipBowler = view.findViewById(R.id.chipBowler)
        chipAllRounder = view.findViewById(R.id.chipAllRounder)
        chipWicketKeeper = view.findViewById(R.id.chipWicketKeeper)

        rvPlayers.layoutManager = LinearLayoutManager(context)
        
        btnApplyFilters.setOnClickListener {
            loadPlayers()
        }

        setupFilters()

        fabAddPlayer.setOnClickListener {
            startActivity(Intent(requireContext(), AddPlayerActivity::class.java))
        }

        // Initialize with default data
        loadPlayers()

        return view
    }

    private fun setupFilters() {
        val chips = listOf(chipAll, chipBatsman, chipBowler, chipAllRounder, chipWicketKeeper)
        
        chips.forEach { chip ->
            chip.setOnClickListener {
                selectedRole = (it as TextView).text.toString()
                
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
                
                loadPlayers()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPlayers()
    }

    private fun loadPlayers() {
        val area = etFilterArea.text.toString().trim()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val players = withContext(Dispatchers.IO) { 
                    SupabaseHelper.getFilteredPlayers(selectedRole, area) 
                }
                if (players.isEmpty()) {
                    tvEmptyState.text = "No players found for these filters."
                    tvEmptyState.visibility = View.VISIBLE
                    rvPlayers.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    rvPlayers.visibility = View.VISIBLE
                    
                    val prefs = requireContext().getSharedPreferences("CrickzyPrefs", android.content.Context.MODE_PRIVATE)
                    val currentUserId = prefs.getLong("USER_ID", -1L)
                    
                    rvPlayers.adapter = PlayerAdapter(players, currentUserId) { player ->
                        showDeletePlayerDialog(player)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FindPlayers", "Error loading players", e)
                tvEmptyState.text = "Failed to load players."
                tvEmptyState.visibility = View.VISIBLE
                rvPlayers.visibility = View.GONE
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
                    loadPlayers()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }
}
