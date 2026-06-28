package com.example.crickzy.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.models.Team

class TeamAdapter(
    private val teamList: List<Team>,
    private val currentUserId: Long = -1L,
    private val onDeleteClick: ((Team) -> Unit)? = null
) : RecyclerView.Adapter<TeamAdapter.TeamViewHolder>() {

    class TeamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvTeamName)
        val tvLoc: TextView = itemView.findViewById(R.id.tvTeamLoc)
        val tvReqRole: TextView = itemView.findViewById(R.id.tvTeamReqRole)
        val tvDate: TextView = itemView.findViewById(R.id.tvTeamDate)
        val btnJoinTeam: Button = itemView.findViewById(R.id.btnJoinTeam)
        val ivDeleteTeam: ImageView = itemView.findViewById(R.id.ivDeleteTeam)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_team, parent, false)
        return TeamViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
        val team = teamList[position]
        holder.tvName.text = team.name
        holder.tvLoc.text = "Location: ${team.location}"
        holder.tvReqRole.text = "Needed: ${team.requiredRole}"
        holder.tvDate.text = "Date: ${team.matchDate} ${team.matchTime}"
        
        if (team.addedBy != -1L && team.addedBy == currentUserId) {
            holder.ivDeleteTeam.visibility = View.VISIBLE
            holder.ivDeleteTeam.setOnClickListener {
                onDeleteClick?.invoke(team)
            }
        } else {
            holder.ivDeleteTeam.visibility = View.GONE
        }

        holder.btnJoinTeam.setOnClickListener {
            val context = holder.itemView.context
            val msg = "Hi! I want to join ${team.name} for the match on ${team.matchDate} at ${team.location}."
            val phoneNumber = team.whatsapp.ifEmpty { team.phone }
            if (phoneNumber.isNotEmpty()) {
                try {
                    val uri = android.net.Uri.parse("whatsapp://send?phone=$phoneNumber&text=${java.net.URLEncoder.encode(msg, "UTF-8")}")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val smsIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("sms:$phoneNumber"))
                    smsIntent.putExtra("sms_body", msg)
                    context.startActivity(smsIntent)
                }
            } else {
                android.widget.Toast.makeText(context, "No contact number available", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = teamList.size
}
