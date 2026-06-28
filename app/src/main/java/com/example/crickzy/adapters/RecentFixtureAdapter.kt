package com.example.crickzy.adapters

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.activities.TournamentDetailActivity
import com.example.crickzy.models.Fixture

class RecentFixtureAdapter(
    private val fixtureList: List<Fixture>
) : RecyclerView.Adapter<RecentFixtureAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvMatchNum: TextView = view.findViewById(R.id.tvMatchNum)
        val tvRound: TextView = view.findViewById(R.id.tvRound)
        val tvTeam1: TextView = view.findViewById(R.id.tvFixTeam1)
        val tvTeam2: TextView = view.findViewById(R.id.tvFixTeam2)
        val tvStatus: TextView = view.findViewById(R.id.tvFixStatus)
        val btnStartMatch: Button = view.findViewById(R.id.btnStartMatch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fixture, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val fixture = fixtureList[position]
        
        holder.tvMatchNum.text = "Match ${fixture.matchNumber}"
        holder.tvRound.text = fixture.round
        holder.tvTeam1.text = fixture.team1Name
        holder.tvTeam2.text = fixture.team2Name
        
        // Hide start match button on home page; tapping the card will take them to Tournament Detail
        holder.btnStartMatch.visibility = View.GONE

        val roundColor = when (fixture.round) {
            "Final" -> "#FFD54F"
            "Semi-Final" -> "#80CBC4"
            "Quarter-Final" -> "#81C784"
            "Quarter-Finals" -> "#81C784"
            "Semi-Finals" -> "#80CBC4"
            else -> "#80CBC4"
        }
        holder.tvRound.setTextColor(Color.parseColor(roundColor))

        when (fixture.status) {
            "Upcoming" -> {
                holder.tvStatus.text = "Upcoming"
                holder.tvStatus.setTextColor(Color.parseColor("#FF8F00"))
            }
            "Live" -> {
                holder.tvStatus.text = "● Live"
                holder.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
            }
            "Completed" -> {
                holder.tvStatus.text = "Completed"
                holder.tvStatus.setTextColor(Color.parseColor("#42A5F5"))
            }
            else -> {
                holder.tvStatus.text = fixture.status
                holder.tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
            }
        }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, TournamentDetailActivity::class.java).apply {
                putExtra("tournamentId", fixture.tournamentId)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = fixtureList.size
}
