package com.example.crickzy.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.models.Request
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.crickzy.database.SupabaseHelper
import com.example.crickzy.utils.NotificationHelper

class RequestAdapter(private val requestList: List<Request>) : RecyclerView.Adapter<RequestAdapter.RequestViewHolder>() {

    class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRequestSender: TextView = itemView.findViewById(R.id.tvRequestSender)
        val tvRequestStatus: TextView = itemView.findViewById(R.id.tvRequestStatus)
        val btnAccept: Button = itemView.findViewById(R.id.btnAcceptRequest)
        val btnDecline: Button = itemView.findViewById(R.id.btnDeclineRequest)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requestList[position]
        holder.tvRequestSender.text = "${request.playerName} -> ${request.teamName}"
        holder.tvRequestStatus.text = "Status: ${request.status}"

        if (request.status == "Pending") {
            holder.btnAccept.visibility = View.VISIBLE
            holder.btnDecline.visibility = View.VISIBLE
        } else {
            holder.btnAccept.visibility = View.GONE
            holder.btnDecline.visibility = View.GONE
        }

        holder.btnAccept.setOnClickListener {
            holder.itemView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                val success = SupabaseHelper.updateRequestStatus(request.id, "Accepted")
                withContext(Dispatchers.Main) {
                    if (success) {
                        NotificationHelper.sendSms(
                            holder.itemView.context, 
                            "1234567890", 
                            "Your request (${request.playerName}) has been Accepted!"
                        )
                        holder.tvRequestStatus.text = "Status: Accepted"
                        holder.btnAccept.visibility = View.GONE
                        holder.btnDecline.visibility = View.GONE
                    }
                }
            }
        }

        holder.btnDecline.setOnClickListener {
            holder.itemView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                val success = SupabaseHelper.updateRequestStatus(request.id, "Declined")
                withContext(Dispatchers.Main) {
                    if (success) {
                        NotificationHelper.sendEmail(
                            holder.itemView.context, 
                            "test@example.com", 
                            "Request Declined", 
                            "Unfortunately, your request (${request.playerName}) to team ${request.teamName} was declined."
                        )
                        holder.tvRequestStatus.text = "Status: Declined"
                        holder.btnAccept.visibility = View.GONE
                        holder.btnDecline.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = requestList.size
}
