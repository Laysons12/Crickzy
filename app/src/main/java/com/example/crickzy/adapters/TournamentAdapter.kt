package com.example.crickzy.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.activities.TournamentDetailActivity
import com.example.crickzy.database.SupabaseHelper
import com.example.crickzy.models.Tournament
import kotlinx.coroutines.*

class TournamentAdapter(
    private val tournamentList: List<Tournament>,
    private val currentUserEmail: String = "",
    private val onDeleteCallback: (() -> Unit)? = null
) : RecyclerView.Adapter<TournamentAdapter.TournamentViewHolder>() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    class TournamentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTournamentName: TextView = itemView.findViewById(R.id.tvTournamentName)
        val tvTournamentLocation: TextView = itemView.findViewById(R.id.tvTournamentLocation)
        val tvTournamentGround: TextView = itemView.findViewById(R.id.tvTournamentGround)
        val tvTournamentOvers: TextView = itemView.findViewById(R.id.tvTournamentOvers)
        val tvTournamentBallType: TextView = itemView.findViewById(R.id.tvTournamentBallType)
        val tvTournamentDates: TextView = itemView.findViewById(R.id.tvTournamentDates)
        val tvTournamentFee: TextView = itemView.findViewById(R.id.tvTournamentFee)
        val tvTournamentPrize: TextView = itemView.findViewById(R.id.tvTournamentPrize)
        val tvTeamCount: TextView = itemView.findViewById(R.id.tvTeamCount)
        val tvFormat: TextView = itemView.findViewById(R.id.tvFormat)
        val btnViewTournament: Button = itemView.findViewById(R.id.btnViewTournament)
        val btnDeleteTournament: Button = itemView.findViewById(R.id.btnDeleteTournament)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TournamentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tournament, parent, false)
        return TournamentViewHolder(view)
    }

    override fun onBindViewHolder(holder: TournamentViewHolder, position: Int) {
        val tournament = tournamentList[position]
        val context = holder.itemView.context

        holder.tvTournamentName.text = tournament.name
        holder.tvTournamentLocation.text = "📍 ${tournament.location}"
        holder.tvTournamentGround.text = "🏟️ ${tournament.groundName}"
        holder.tvTournamentOvers.text = "${tournament.overs} overs"
        holder.tvTournamentBallType.text = tournament.ballType
        holder.tvTournamentDates.text = "${tournament.startDate} - ${tournament.endDate}"
        holder.tvTournamentFee.text = "Fee: ₹${tournament.entryFee}"
        holder.tvTournamentPrize.text = "Prize: ${tournament.prizePool}"

        holder.tvTeamCount.text = "${tournament.teamCount} Teams"

        val isOrganizer = currentUserEmail.isNotEmpty() && 
            tournament.organizerEmail.equals(currentUserEmail, ignoreCase = true)
        
        // We will pass the completion status via a field in Tournament model or similar
        // For now, let's assume it's true if the format is set and fixtures are created (placeholder logic or use the new field)
        // Better: let's use the format and fixturesCreated as a proxy or update model again
        val isCompleted = tournament.fixturesCreated && tournament.format.isNotEmpty() // Simple proxy for now
        
        if (isOrganizer) {
            holder.btnDeleteTournament.visibility = View.VISIBLE
            holder.btnDeleteTournament.setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Delete Tournament")
                    .setMessage("Are you sure you want to delete \"${tournament.name}\"? This will remove all teams, fixtures, and matches.")
                    .setPositiveButton("Delete") { _, _ ->
                        scope.launch {
                            withContext(Dispatchers.IO) { SupabaseHelper.deleteTournament(tournament.id) }
                            Toast.makeText(context, "Tournament deleted", Toast.LENGTH_SHORT).show()
                            onDeleteCallback?.invoke()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            holder.btnDeleteTournament.visibility = View.GONE
        }

        if (tournament.format.isNotEmpty()) {
            holder.tvFormat.text = tournament.format
            holder.tvFormat.visibility = View.VISIBLE
        } else {
            holder.tvFormat.visibility = View.GONE
        }

        holder.btnViewTournament.setOnClickListener {
            val intent = Intent(context, TournamentDetailActivity::class.java)
            intent.putExtra("tournamentId", tournament.id)
            context.startActivity(intent)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, TournamentDetailActivity::class.java)
            intent.putExtra("tournamentId", tournament.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = tournamentList.size
}
