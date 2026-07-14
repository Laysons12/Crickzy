package com.example.crickzy.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val llAiMessage: LinearLayout = view.findViewById(R.id.llAiMessage)
        val llUserMessage: LinearLayout = view.findViewById(R.id.llUserMessage)
        val tvAiText: TextView = view.findViewById(R.id.tvAiText)
        val tvUserText: TextView = view.findViewById(R.id.tvUserText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        if (msg.isUser) {
            holder.llUserMessage.visibility = View.VISIBLE
            holder.llAiMessage.visibility = View.GONE
            holder.tvUserText.text = msg.text
        } else {
            holder.llAiMessage.visibility = View.VISIBLE
            holder.llUserMessage.visibility = View.GONE
            holder.tvAiText.text = msg.text
        }
    }

    override fun getItemCount(): Int = messages.size
}
