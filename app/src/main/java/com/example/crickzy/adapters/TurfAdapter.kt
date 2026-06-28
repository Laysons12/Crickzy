package com.example.crickzy.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.crickzy.R
import com.example.crickzy.activities.TurfBookingActivity
import com.example.crickzy.models.Turf

class TurfAdapter(
    private val turfList: List<Turf>,
    private val currentUserId: Long,
    private val onDeleteClick: (Turf) -> Unit,
    private val onEditClick: (Turf) -> Unit
) : RecyclerView.Adapter<TurfAdapter.TurfViewHolder>() {

    class TurfViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivTurfPhoto: ImageView = itemView.findViewById(R.id.ivTurfPhoto)
        val ivDeleteTurf: ImageView = itemView.findViewById(R.id.ivDeleteTurf)
        val ivEditTurf: ImageView = itemView.findViewById(R.id.ivEditTurf)
        val tvTurfName: TextView = itemView.findViewById(R.id.tvTurfName)
        val tvTurfLocation: TextView = itemView.findViewById(R.id.tvTurfLoc)
        val tvTurfPrice: TextView = itemView.findViewById(R.id.tvTurfPrice)
        val btnBookTurf: Button = itemView.findViewById(R.id.btnBookTurf)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TurfViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_turf, parent, false)
        return TurfViewHolder(view)
    }

    override fun onBindViewHolder(holder: TurfViewHolder, position: Int) {
        val turf = turfList[position]
        
        holder.ivTurfPhoto.load(turf.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_cricket_logo)
            error(R.drawable.ic_cricket_logo)
        }
        
        holder.tvTurfName.text = turf.name
        holder.tvTurfLocation.text = "Location: ${turf.location}"
        holder.tvTurfPrice.text = "Price: ₹${turf.pricePerHour}/hr"

        if (turf.addedBy != -1L && turf.addedBy == currentUserId) {
            holder.ivDeleteTurf.visibility = View.VISIBLE
            holder.ivEditTurf.visibility = View.VISIBLE
            holder.ivDeleteTurf.setOnClickListener {
                onDeleteClick(turf)
            }
            holder.ivEditTurf.setOnClickListener {
                onEditClick(turf)
            }
        } else {
            holder.ivDeleteTurf.visibility = View.GONE
            holder.ivEditTurf.visibility = View.GONE
        }

        holder.btnBookTurf.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, TurfBookingActivity::class.java).apply {
                putExtra("TURF_ID", turf.id)
                putExtra("TURF_NAME", turf.name)
                putExtra("TURF_LOC", turf.location)
                putExtra("TURF_PRICE", turf.pricePerHour)
                putExtra("TURF_IMG", turf.imageUrl)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = turfList.size
}
