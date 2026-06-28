package com.example.crickzy.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.crickzy.R
import com.example.crickzy.database.SupabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalyticsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_analytics, container, false)

        val tvTotalUsers = view.findViewById<TextView>(R.id.tvTotalUsers)
        val tvTotalTeams = view.findViewById<TextView>(R.id.tvTotalTeams)
        val tvTotalMatches = view.findViewById<TextView>(R.id.tvTotalMatches)

        viewLifecycleOwner.lifecycleScope.launch {
            val userCount = withContext(Dispatchers.IO) { SupabaseHelper.getUserCount() }
            val teamCount = withContext(Dispatchers.IO) { SupabaseHelper.getTotalTournamentTeamCount() }
            val matchCount = withContext(Dispatchers.IO) { SupabaseHelper.getAllMatches().size }
            tvTotalUsers.text = userCount.toString()
            tvTotalTeams.text = teamCount.toString()
            tvTotalMatches.text = matchCount.toString()
        }

        return view
    }
}
