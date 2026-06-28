package com.example.crickzy.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.activities.OrganizeTournamentActivity
import com.example.crickzy.adapters.TournamentAdapter
import com.example.crickzy.database.SupabaseHelper
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TournamentsFragment : Fragment() {

    private lateinit var rvTournaments: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var fabOrganize: FloatingActionButton
    private lateinit var fabScanQR: ExtendedFloatingActionButton

    private lateinit var chipAll: TextView
    private lateinit var chipLeague: TextView
    private lateinit var chipKnockout: TextView
    private lateinit var chipODI: TextView
    private var selectedCategory: String = "All"
    private var allTournaments: List<com.example.crickzy.models.Tournament> = emptyList()
    
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val uri = Uri.parse(result.contents)
            val teamId = uri.getQueryParameter("teamId")?.toLongOrNull() ?: -1L
            val tourId = uri.getQueryParameter("tournamentId")?.toLongOrNull() ?: -1L
            if (teamId != -1L && tourId != -1L) {
                // Implement join logic or navigate
                Toast.makeText(requireContext(), "Joining Team $teamId in Tournament $tourId", Toast.LENGTH_SHORT).show()
                // ... join logic here ...
            } else {
                Toast.makeText(requireContext(), "Invalid QR Code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tournaments, container, false)
        
        rvTournaments = view.findViewById(R.id.rvTournaments)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        fabOrganize = view.findViewById(R.id.fabOrganizeTournament)
        fabScanQR = view.findViewById(R.id.fabScanQRTournament)
        
        chipAll = view.findViewById(R.id.chipAll)
        chipLeague = view.findViewById(R.id.chipLeague)
        chipKnockout = view.findViewById(R.id.chipKnockout)
        chipODI = view.findViewById(R.id.chipODI)

        rvTournaments.layoutManager = LinearLayoutManager(context)
        
        setupCategoryFilters()

        fabOrganize.setOnClickListener {
            startActivity(Intent(activity, OrganizeTournamentActivity::class.java))
        }

        fabScanQR.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scan team QR code to join")
            options.setCameraId(0)
            options.setBeepEnabled(true)
            options.setBarcodeImageEnabled(true)
            barcodeLauncher.launch(options)
        }

        return view
    }

    private fun setupCategoryFilters() {
        val chips = listOf(chipAll, chipLeague, chipKnockout, chipODI)
        chips.forEach { chip ->
            chip.setOnClickListener {
                selectedCategory = when(chip.id) {
                    R.id.chipAll -> "All"
                    R.id.chipLeague -> "League"
                    R.id.chipKnockout -> "Knockout"
                    R.id.chipODI -> "ODI"
                    else -> "All"
                }

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
                
                displayTournaments()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTournaments()
    }

    private fun loadTournaments() {
        viewLifecycleOwner.lifecycleScope.launch {
            allTournaments = withContext(Dispatchers.IO) { SupabaseHelper.getAllTournamentsWithStats() }
            displayTournaments()
        }
    }

    private fun displayTournaments() {
        val filtered = if (selectedCategory == "All") {
            allTournaments
        } else {
            allTournaments.filter { it.format.equals(selectedCategory, ignoreCase = true) }
        }

        if (filtered.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            rvTournaments.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvTournaments.visibility = View.VISIBLE
            
            // Sort: latest first, but completed tournaments go to the bottom
            val sorted = filtered.sortedWith(
                compareBy<com.example.crickzy.models.Tournament> { 
                    if (it.fixturesCreated && it.format.isNotEmpty()) 1 else 0
                }.thenByDescending { it.id }
            )
            
            val prefs = requireContext().getSharedPreferences("CrickzyPrefs", android.content.Context.MODE_PRIVATE)
            val currentUserEmail = prefs.getString("loggedInEmail", "") ?: ""
            
            rvTournaments.adapter = TournamentAdapter(sorted, currentUserEmail) {
                loadTournaments()
            }
        }
    }
}
