package com.example.crickzy.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.models.Player

class PlayerAdapter(
    private val playerList: List<Player>,
    private val currentUserId: Long = -1L,
    private val onDeleteClick: ((Player) -> Unit)? = null
) : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

    class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivRoleIcon: ImageView = itemView.findViewById(R.id.ivRoleIcon)
        val tvName: TextView = itemView.findViewById(R.id.tvPlayerName)
        val tvRole: TextView = itemView.findViewById(R.id.tvPlayerRole)
        val tvSkill: TextView = itemView.findViewById(R.id.tvPlayerSkill)
        val tvAvailability: TextView = itemView.findViewById(R.id.tvAvailability)
        val btnRequest: Button = itemView.findViewById(R.id.btnRequest)
        val ivDeletePlayer: ImageView = itemView.findViewById(R.id.ivDeletePlayer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_player, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val player = playerList[position]
        holder.tvName.text = player.name
        holder.tvRole.text = player.role
        holder.tvSkill.text = "Skill: ${player.skillRating}/100"

        if (player.isAvailable) {
            holder.tvAvailability.text = "Available"
            holder.tvAvailability.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
        } else {
            holder.tvAvailability.text = "Unavailable"
            holder.tvAvailability.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
        }

        // Set role-specific icon
        val iconRes = when (player.role.lowercase()) {
            "batsman" -> R.drawable.ic_role_batsman
            "bowler" -> R.drawable.ic_role_bowler
            "all-rounder" -> R.drawable.ic_role_allrounder
            "wicket-keeper" -> R.drawable.ic_role_wicketkeeper
            else -> R.drawable.ic_role_batsman
        }
        holder.ivRoleIcon.setImageResource(iconRes)

        if (player.addedBy != -1L && player.addedBy == currentUserId) {
            holder.ivDeletePlayer.visibility = View.VISIBLE
            holder.ivDeletePlayer.setOnClickListener {
                onDeleteClick?.invoke(player)
            }
        } else {
            holder.ivDeletePlayer.visibility = View.GONE
        }

        holder.btnRequest.setOnClickListener {
            val context = holder.itemView.context
            val msg = "Hi! I would like to invite you (${player.name}) to join my team."
            val phoneNumber = player.whatsapp.ifEmpty { player.phone }
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

    override fun getItemCount(): Int = playerList.size
}
