package com.example.crickzy.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.activities.ScoreActivity
import com.example.crickzy.adapters.MatchAdapter
import com.example.crickzy.database.SupabaseHelper
import com.example.crickzy.models.Match
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveScoresFragment : Fragment() {

    private lateinit var rvLiveScores: RecyclerView
    private lateinit var tvEmptyState: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_live_scores, container, false)

        rvLiveScores = view.findViewById(R.id.rvLiveScores)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        rvLiveScores.layoutManager = LinearLayoutManager(context)

        val fabStartMatch = view.findViewById<FloatingActionButton>(R.id.fabStartMatch)
        fabStartMatch.setOnClickListener {
            showNewMatchDialog()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        loadMatches()
    }

    private fun loadMatches() {
        viewLifecycleOwner.lifecycleScope.launch {
            val matches = withContext(Dispatchers.IO) { SupabaseHelper.getAllMatches() }
            if (matches.isEmpty()) {
                tvEmptyState.visibility = View.VISIBLE
                rvLiveScores.visibility = View.GONE
            } else {
                tvEmptyState.visibility = View.GONE
                rvLiveScores.visibility = View.VISIBLE
                rvLiveScores.adapter = MatchAdapter(matches)
            }
        }
    }

    private fun showNewMatchDialog() {
        val ctx = requireContext()
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_new_match, null)
        val etTeam1 = dialogView.findViewById<EditText>(R.id.etDialogTeam1)
        val etTeam2 = dialogView.findViewById<EditText>(R.id.etDialogTeam2)
        val etOvers = dialogView.findViewById<EditText>(R.id.etDialogOvers)
        val etTeam1Players = dialogView.findViewById<EditText>(R.id.etDialogTeam1Players)
        val etTeam2Players = dialogView.findViewById<EditText>(R.id.etDialogTeam2Players)

        AlertDialog.Builder(ctx)
            .setTitle("🏏 Start New Match")
            .setView(dialogView)
            .setPositiveButton("Start Match") { _, _ ->
                val t1 = etTeam1.text.toString().trim()
                val t2 = etTeam2.text.toString().trim()
                val oversStr = etOvers.text.toString().trim()
                val overs = oversStr.toIntOrNull() ?: 0

                if (t1.isEmpty()) { etTeam1.error = "Team 1 name required"; etTeam1.requestFocus(); return@setPositiveButton }
                if (t2.isEmpty()) { etTeam2.error = "Team 2 name required"; etTeam2.requestFocus(); return@setPositiveButton }
                if (oversStr.isEmpty() || overs <= 0) { etOvers.error = "Valid overs required"; etOvers.requestFocus(); return@setPositiveButton }


                val t1Players = etTeam1Players.text.toString().trim()
                    .lines().map { it.trim() }.filter { it.isNotEmpty() }
                val t2Players = etTeam2Players.text.toString().trim()
                    .lines().map { it.trim() }.filter { it.isNotEmpty() }

                viewLifecycleOwner.lifecycleScope.launch {
                    val match = Match(team1Name = t1, team2Name = t2, matchStatus = "Ongoing")
                    val matchId = withContext(Dispatchers.IO) { SupabaseHelper.addMatch(match) }

                    val intent = Intent(ctx, ScoreActivity::class.java)
                    intent.putExtra("matchId", matchId)
                    intent.putExtra("team1Name", t1)
                    intent.putExtra("team2Name", t2)
                    intent.putExtra("totalOvers", overs)
                    intent.putExtra("isNewMatch", true)
                    if (t1Players.isNotEmpty()) intent.putStringArrayListExtra("team1Players", ArrayList(t1Players))
                    if (t2Players.isNotEmpty()) intent.putStringArrayListExtra("team2Players", ArrayList(t2Players))
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
