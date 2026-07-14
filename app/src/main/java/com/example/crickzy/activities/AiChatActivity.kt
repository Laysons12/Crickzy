package com.example.crickzy.activities

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.adapters.ChatAdapter
import com.example.crickzy.adapters.ChatMessage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AiChatActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    // Chat history for multi-turn context
    private val chatHistory = mutableListOf<Map<String, String>>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Change this to your backend server URL
    private val CHAT_API_URL = "https://jsmnhfwuuwijemozuigs.supabase.co/functions/v1/chat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        toolbar.setNavigationOnClickListener { onBackPressed() }

        rvChat = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        adapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = adapter

        // Welcome message
        addAiMessage("Hey there! 🏏 I'm Crickzy AI — your personal cricket assistant.\n\nAsk me about cricket rules, scoring, match strategy, player stats, tournament scheduling, or anything cricket-related!\n\nWhat can I help you with today? ⚡")

        btnSend.setOnClickListener { sendMessage() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        etMessage.setText("")
        addUserMessage(text)

        // Add typing indicator
        addAiMessage("Thinking... 🏏")

        lifecycleScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) { callChatApi(text) }
                // Remove typing indicator
                if (messages.isNotEmpty() && messages.last().text == "Thinking... 🏏") {
                    messages.removeAt(messages.size - 1)
                }
                addAiMessage(reply)

                // Track history for multi-turn
                chatHistory.add(mapOf("role" to "user", "text" to text))
                chatHistory.add(mapOf("role" to "model", "text" to reply))
            } catch (e: Exception) {
                // Remove typing indicator
                if (messages.isNotEmpty() && messages.last().text == "Thinking... 🏏") {
                    messages.removeAt(messages.size - 1)
                }
                addAiMessage("Sorry, I couldn't connect right now. Check your internet and try again! 🔄")
                android.util.Log.e("AiChat", "Chat error: ${e.message}", e)
            }
        }
    }

    private fun callChatApi(message: String): String {
        val jsonBody = JsonObject().apply {
            addProperty("message", message)
            val historyArray = com.google.gson.JsonArray()
            for (h in chatHistory) {
                val obj = JsonObject()
                obj.addProperty("role", h["role"])
                obj.addProperty("text", h["text"])
                historyArray.add(obj)
            }
            add("history", historyArray)
        }

        val request = Request.Builder()
            .url(CHAT_API_URL)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw Exception("API Error ${response.code}: $body")
        }

        val json = JsonParser.parseString(body)
        if (json.isJsonObject) {
            val obj = json.asJsonObject
            if (obj.has("reply")) {
                return obj.get("reply").asString
            }
            if (obj.has("error")) {
                return "⚠️ ${obj.get("error").asString}"
            }
        }
        return "I received an unexpected response. Please try again."
    }

    private fun addUserMessage(text: String) {
        messages.add(ChatMessage(text, isUser = true))
        adapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    private fun addAiMessage(text: String) {
        messages.add(ChatMessage(text, isUser = false))
        adapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }
}
