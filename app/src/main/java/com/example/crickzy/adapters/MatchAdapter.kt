package com.example.crickzy.adapters

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.activities.ScoreActivity
import com.example.crickzy.models.Match

class MatchAdapter(private val matchList: List<Match>) :
    RecyclerView.Adapter<MatchAdapter.MatchViewHolder>() {

    class MatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMatchStatus: TextView = itemView.findViewById(R.id.tvMatchStatus)
        val tvTeam1Name: TextView = itemView.findViewById(R.id.tvTeam1Name)
        val tvTeam1Score: TextView = itemView.findViewById(R.id.tvTeam1Score)
        val tvTeam2Name: TextView = itemView.findViewById(R.id.tvTeam2Name)
        val tvTeam2Score: TextView = itemView.findViewById(R.id.tvTeam2Score)
        val tvMatchResult: TextView = itemView.findViewById(R.id.tvMatchResult)
        val llBatsmanInfo: LinearLayout = itemView.findViewById(R.id.llBatsmanInfo)
        val tvBatsman1Info: TextView = itemView.findViewById(R.id.tvBatsman1Info)
        val tvBatsman2Info: TextView = itemView.findViewById(R.id.tvBatsman2Info)
        val tvBowlerInfo: TextView = itemView.findViewById(R.id.tvBowlerInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_match, parent, false)
        return MatchViewHolder(view)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        val match = matchList[position]

        holder.tvMatchStatus.text = match.matchStatus
        holder.tvTeam1Name.text = match.team1Name
        holder.tvTeam2Name.text = match.team2Name

        holder.tvTeam1Score.text = "${match.team1Runs}/${match.team1Wickets} (${match.getTeam1OversDisplay()})"
        holder.tvTeam2Score.text = "${match.team2Runs}/${match.team2Wickets} (${match.getTeam2OversDisplay()})"

        if (match.matchStatus == "Finished") {
            holder.tvMatchStatus.setTextColor(Color.parseColor("#F57F17"))
            holder.tvMatchResult.visibility = View.VISIBLE
            holder.tvMatchResult.text = match.winner
            holder.llBatsmanInfo.visibility = View.GONE
        } else {
            holder.tvMatchStatus.setTextColor(Color.parseColor("#2E7D32"))
            holder.tvMatchResult.visibility = View.GONE

            if (match.currentBatsman1.isNotEmpty()) {
                holder.llBatsmanInfo.visibility = View.VISIBLE
                val sr1 = match.getBatsman1SR()
                val sr2 = match.getBatsman2SR()
                holder.tvBatsman1Info.text = "★ ${match.currentBatsman1} ${match.batsman1Runs}(${match.batsman1Balls}) SR:$sr1 | 4s:${match.batsman1Fours} 6s:${match.batsman1Sixes}"
                holder.tvBatsman2Info.text = "  ${match.currentBatsman2} ${match.batsman2Runs}(${match.batsman2Balls}) SR:$sr2 | 4s:${match.batsman2Fours} 6s:${match.batsman2Sixes}"

                if (match.currentBowler.isNotEmpty()) {
                    val bowlerOversDisplay = match.getBowlerOversDisplay()
                    val bowlerEcon = match.getBowlerEconomy()
                    holder.tvBowlerInfo.text = "🔴 ${match.currentBowler} ${bowlerOversDisplay}-${match.bowlerMaidens}-${match.bowlerRuns}-${match.bowlerWickets} Econ:$bowlerEcon"
                    holder.tvBowlerInfo.visibility = View.VISIBLE
                } else {
                    holder.tvBowlerInfo.visibility = View.GONE
                }
            } else {
                holder.llBatsmanInfo.visibility = View.GONE
            }
        }

        // Tap ongoing match to resume scoring
        if (match.matchStatus == "Ongoing") {
            holder.itemView.setOnClickListener {
                val context = holder.itemView.context
                val intent = Intent(context, ScoreActivity::class.java)
                intent.putExtra("matchId", match.id)
                intent.putExtra("team1Name", match.team1Name)
                intent.putExtra("team2Name", match.team2Name)
                intent.putExtra("tournamentId", match.tournamentId)
                intent.putExtra("fixtureId", match.fixtureId)
                context.startActivity(intent)
            }
        } else {
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int = matchList.size
}
