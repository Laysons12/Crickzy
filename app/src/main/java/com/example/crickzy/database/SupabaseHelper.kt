package com.example.crickzy.database

import android.util.Log
import com.example.crickzy.models.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * SupabaseHelper — Replaces DatabaseHelper.
 * All reads/writes go to Supabase via its PostgREST API.
 *
 * IMPORTANT: All methods are BLOCKING network calls. They MUST be called from
 * a background thread (use lifecycleScope.launch(Dispatchers.IO) { ... }).
 */
object SupabaseHelper {

    private const val TAG = "SupabaseHelper"

    private const val SUPABASE_URL = "https://jsmnhfwuuwijemozuigs.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_X9wDxlgJeTOJgEYiwgTyKA_LMTO7Fqd"
    private const val REST_URL = "$SUPABASE_URL/rest/v1"

    private val JSON_MEDIA = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // ========== HTTP Helpers ==========

    private fun buildRequest(url: String): okhttp3.Request.Builder {
        return okhttp3.Request.Builder()
            .url(url)
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_KEY")
    }



    /** POST — insert a row. Returns the inserted row's ID, or -1 on failure. */
    private fun postAndGetId(table: String, json: String): Long {
        try {
            val request = buildRequest("$REST_URL/$table")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(json.toRequestBody(JSON_MEDIA))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            Log.d(TAG, "POST /$table → ${response.code}: $body")
            if (response.isSuccessful) {
                val element = safeParse(body)
                if (element != null && element.isJsonArray) {
                    val arr = element.asJsonArray
                    if (arr.size() > 0) {
                        return arr[0].asJsonObject.get("id").asLong
                    }
                }
            } else {
                lastError = "HTTP ${response.code}: $body"
                Log.e(TAG, "POST /$table FAILED: $lastError")
            }
        } catch (e: Exception) {
            lastError = e.message ?: "Unknown error"
            Log.e(TAG, "POST /$table Exception: $lastError")
        }
        return -1
    }

    var lastError: String = "" // For UI to show exact failure reason

    /** GET — fetch rows. Returns raw JSON string. */
    private fun get(table: String, query: String = ""): String {
        try {
            val url = if (query.isEmpty()) "$REST_URL/$table?select=*" else "$REST_URL/$table?$query"
            val request = buildRequest(url)
                .addHeader("Accept", "application/json")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            Log.d(TAG, "GET /$table → ${response.code}")
            if (!response.isSuccessful) {
                Log.e(TAG, "GET /$table FAILED (${response.code}): $body")
                return "[]"
            }
            return body
        } catch (e: Exception) {
            Log.e(TAG, "GET /$table failed: ${e.message}")
        }
        return "[]"
    }

    /** PATCH — update rows matching query. */
    private fun patch(table: String, query: String, json: String): Boolean {
        try {
            val request = buildRequest("$REST_URL/$table?$query")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(json.toRequestBody(JSON_MEDIA))
                .build()
            val response = client.newCall(request).execute()
            Log.d(TAG, "PATCH /$table?$query → ${response.code}")
            return response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "PATCH /$table failed: ${e.message}")
        }
        return false
    }

    /** DELETE — remove rows matching query. */
    private fun delete(table: String, query: String): Boolean {
        try {
            val request = buildRequest("$REST_URL/$table?$query")
                .delete()
                .build()
            val response = client.newCall(request).execute()
            Log.d(TAG, "DELETE /$table?$query → ${response.code}")
            return response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "DELETE /$table failed: ${e.message}")
        }
        return false
    }

    // ========== USER AUTH ==========

    fun addUser(name: String, email: String, pass: String): Long {
        val json = JsonObject().apply {
            addProperty("name", name)
            addProperty("email", email)
            addProperty("password", pass)
        }.toString()
        return postAndGetId("users", json)
    }

    fun checkUser(email: String, pass: String): Boolean {
        return try {
            val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
            val encodedPass = java.net.URLEncoder.encode(pass, "UTF-8")
            val body = get("users", "select=id&email=eq.$encodedEmail&password=eq.$encodedPass")
            val element = safeParse(body)
            if (element != null && element.isJsonArray) {
                element.asJsonArray.size() > 0
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "checkUser failed: ${e.message}")
            false
        }
    }

    private fun safeParse(json: String): com.google.gson.JsonElement? {
        return try {
            if (json.trim().startsWith("{") || json.trim().startsWith("[")) {
                JsonParser.parseString(json)
            } else null
        } catch (e: Exception) { null }
    }

    // Null-safe JSON accessors — JsonObject.get() returns JsonNull for null values,
    // which is NOT Kotlin null, so ?.asString etc. still throw.
    private fun com.google.gson.JsonObject.safeString(key: String, default: String = ""): String {
        val el = this.get(key) ?: return default
        return if (el.isJsonNull) default else try { el.asString } catch (e: Exception) { default }
    }
    private fun com.google.gson.JsonObject.safeInt(key: String, default: Int = 0): Int {
        val el = this.get(key) ?: return default
        return if (el.isJsonNull) default else try { el.asInt } catch (e: Exception) { default }
    }
    private fun com.google.gson.JsonObject.safeLong(key: String, default: Long = -1): Long {
        val el = this.get(key) ?: return default
        return if (el.isJsonNull) default else try { el.asLong } catch (e: Exception) { default }
    }
    private fun com.google.gson.JsonObject.safeDouble(key: String, default: Double = 0.0): Double {
        val el = this.get(key) ?: return default
        return if (el.isJsonNull) default else try { el.asDouble } catch (e: Exception) { default }
    }
    private fun com.google.gson.JsonObject.safeBool(key: String, default: Boolean = false): Boolean {
        val el = this.get(key) ?: return default
        return if (el.isJsonNull) default else try { el.asBoolean } catch (e: Exception) { default }
    }
    private fun com.google.gson.JsonObject.safeFloat(key: String, default: Float = 0f): Float {
        val el = this.get(key) ?: return default
        return if (el.isJsonNull) default else try { el.asFloat } catch (e: Exception) { default }
    }

    fun getUserNameByEmail(email: String): String {
        return try {
            val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
            val body = get("users", "select=name&email=eq.$encodedEmail")
            val element = safeParse(body)
            if (element != null && element.isJsonArray) {
                val arr = element.asJsonArray
                if (arr.size() > 0) arr[0].asJsonObject.get("name").asString else ""
            } else ""
        } catch (e: Exception) {
            Log.e(TAG, "getUserNameByEmail failed: ${e.message}")
            ""
        }
    }

    fun getUserIdByEmail(email: String): Long {
        return try {
            val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
            val body = get("users", "select=id&email=eq.$encodedEmail")
            val element = safeParse(body)
            if (element != null && element.isJsonArray) {
                val arr = element.asJsonArray
                if (arr.size() > 0) arr[0].asJsonObject.get("id").asLong else -1L
            } else -1L
        } catch (e: Exception) {
            Log.e(TAG, "getUserIdByEmail failed: ${e.message}")
            -1L
        }
    }

    fun getPasswordByEmail(email: String): String {
        return try {
            val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
            val body = get("users", "select=password&email=eq.$encodedEmail")
            val element = safeParse(body)
            if (element != null && element.isJsonArray) {
                val arr = element.asJsonArray
                if (arr.size() > 0) arr[0].asJsonObject.get("password").asString else ""
            } else ""
        } catch (e: Exception) {
            Log.e(TAG, "getPasswordByEmail failed: ${e.message}")
            ""
        }
    }

    fun getUserCount(): Int {
        return try {
            val body = get("users", "select=id")
            val element = safeParse(body)
            if (element != null && element.isJsonArray) element.asJsonArray.size() else 0
        } catch (e: Exception) { Log.e(TAG, "getUserCount failed: ${e.message}"); 0 }
    }

    fun getTotalTournamentTeamCount(): Int {
        return try {
            val body = get("tournament_teams", "select=id")
            val element = safeParse(body)
            if (element != null && element.isJsonArray) element.asJsonArray.size() else 0
        } catch (e: Exception) { Log.e(TAG, "getTotalTournamentTeamCount failed: ${e.message}"); 0 }
    }

    // ========== PLAYER CRUD ==========

    fun addPlayer(player: Player): Long {
        val json = JsonObject().apply {
            addProperty("name", player.name)
            addProperty("phone", player.phone)
            addProperty("role", player.role)
            addProperty("match_type", player.matchType)
            addProperty("is_wicket_keeper", player.isWicketKeeper)
            addProperty("is_available", player.isAvailable)
            addProperty("skill_rating", player.skillRating)
            addProperty("exp_level", player.expLevel)
            addProperty("availability_date", player.availabilityDate)
            addProperty("match_time", player.matchTime)
            addProperty("profile_image_uri", player.profileImageUri)
            addProperty("ball_type", player.ballType)
            addProperty("area", player.area)
            addProperty("phone", player.phone)
            addProperty("whatsapp", player.whatsapp)
            addProperty("added_by", player.addedBy)
        }.toString()
        return postAndGetId("players", json)
    }

    fun getPlayerByName(name: String): Player? {
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        val body = get("players", "select=*&name=eq.$encodedName&limit=1")
        val list = parsePlayers(body)
        return list.firstOrNull()
    }

    fun updatePlayer(player: Player): Boolean {
        val json = JsonObject().apply {
            addProperty("name", player.name)
            addProperty("phone", player.phone)
            addProperty("role", player.role)
            addProperty("match_type", player.matchType)
            addProperty("is_wicket_keeper", player.isWicketKeeper)
            addProperty("is_available", player.isAvailable)
            addProperty("skill_rating", player.skillRating)
            addProperty("exp_level", player.expLevel)
            addProperty("availability_date", player.availabilityDate)
            addProperty("match_time", player.matchTime)
            addProperty("profile_image_uri", player.profileImageUri)
        }.toString()
        return patch("players", "phone=eq.${player.phone}", json)
    }

    fun getPlayersForTeam(teamId: Long): List<Player> {
        val body = get("tournament_players", "select=*&team_id=eq.$teamId")
        return parsePlayers(body)
    }

    fun deletePlayer(playerId: Long) {
        delete("players", "id=eq.$playerId")
    }

    fun deleteTeam(teamId: Long) {
        delete("teams", "id=eq.$teamId")
    }

    fun getAllPlayers(): List<Player> {
        val body = get("players", "select=*&order=id.desc")
        return parsePlayers(body)
    }

    fun getFilteredPlayers(role: String?, area: String?): List<Player> {
        var query = "select=*&order=id.desc"
        if (!role.isNullOrEmpty() && role != "All" && role != "Any") {
            query += "&role=eq.$role"
        }
        if (!area.isNullOrEmpty()) {
            val encodedArea = java.net.URLEncoder.encode(area, "UTF-8")
            query += "&area=ilike.*$encodedArea*"
        }
        val body = get("players", query)
        return parsePlayers(body)
    }

    private fun parsePlayers(body: String): List<Player> {
        val list = mutableListOf<Player>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return list
            val arr = element.asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                list.add(Player(
                    id = o.safeLong("id"),
                    name = o.safeString("name"),
                    phone = o.safeString("phone"),
                    role = o.safeString("role"),
                    matchType = o.safeString("match_type"),
                    isWicketKeeper = o.safeBool("is_wicket_keeper"),
                    isAvailable = o.safeBool("is_available", true),
                    skillRating = o.safeInt("skill_rating"),
                    expLevel = o.safeFloat("exp_level"),
                    availabilityDate = o.safeString("availability_date"),
                    matchTime = o.safeString("match_time"),
                    profileImageUri = o.safeString("profile_image_uri"),
                    ballType = o.safeString("ball_type"),
                    area = o.safeString("area"),
                    whatsapp = o.safeString("whatsapp"),
                    addedBy = o.safeLong("added_by", -1L)
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "Parse players: ${e.message}") }
        return list
    }

    // ========== TEAM CRUD ==========

    fun addTeam(team: Team): Long {
        val json = JsonObject().apply {
            addProperty("name", team.name)
            addProperty("location", team.location)
            addProperty("required_role", team.requiredRole)
            addProperty("budget_progress", team.budgetProgress)
            addProperty("match_date", team.matchDate)
            addProperty("match_time", team.matchTime)
            addProperty("needs_players", team.needsPlayers)
            addProperty("ball_type", team.ballType)
            addProperty("area", team.area)
            addProperty("phone", team.phone)
            addProperty("whatsapp", team.whatsapp)
            addProperty("added_by", team.addedBy)
        }.toString()
        return postAndGetId("teams", json)
    }

    fun getAllTeams(): List<Team> {
        val body = get("teams", "select=*&order=id.desc")
        return parseTeams(body)
    }

    fun getFilteredTeams(ballType: String?, area: String?): List<Team> {
        var query = "select=*&order=id.desc"
        if (!ballType.isNullOrEmpty() && ballType != "Any") {
            query += "&ball_type=eq.$ballType"
        }
        if (!area.isNullOrEmpty()) {
            val encodedArea = java.net.URLEncoder.encode(area, "UTF-8")
            query += "&area=ilike.*$encodedArea*"
        }
        val body = get("teams", query)
        return parseTeams(body)
    }

    private fun parseTeams(body: String): List<Team> {
        val list = mutableListOf<Team>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return list
            val arr = element.asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                list.add(Team(
                    id = o.safeLong("id"),
                    name = o.safeString("name"),
                    location = o.safeString("location"),
                    requiredRole = o.safeString("required_role"),
                    budgetProgress = o.safeInt("budget_progress"),
                    matchDate = o.safeString("match_date"),
                    matchTime = o.safeString("match_time"),
                    needsPlayers = o.safeBool("needs_players"),
                    ballType = o.safeString("ball_type"),
                    area = o.safeString("area"),
                    phone = o.safeString("phone"),
                    whatsapp = o.safeString("whatsapp"),
                    addedBy = o.safeLong("added_by", -1L)
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "Parse teams: ${e.message}") }
        return list
    }

    // ========== TURF ==========

    fun insertDummyTurfs() {
        // Check if turfs exist first
        val body = get("turfs", "select=id&limit=1")
        val element = safeParse(body)
        if (element == null || !element.isJsonArray) return
        val arr = element.asJsonArray
        if (arr.size() > 0) return

        val turfs = listOf(
            mapOf("name" to "Green Valley Turf", "location" to "Downtown", "price_per_hour" to 1500.0, "image_url" to "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e"),
            mapOf("name" to "Royal Cricket Ground", "location" to "Uptown", "price_per_hour" to 2000.0, "image_url" to "https://images.unsplash.com/photo-1599839619722-39751411ea63"),
            mapOf("name" to "Spartan Arena", "location" to "West End", "price_per_hour" to 1800.0, "image_url" to "https://images.unsplash.com/photo-1531415074968-036ba1b575da"),
            mapOf("name" to "Knight Riders Academy", "location" to "East Coast", "price_per_hour" to 2500.0, "image_url" to "https://images.unsplash.com/photo-1624526267942-ab0ff8a3e972"),
            mapOf("name" to "Lords Local Turf", "location" to "Central Park", "price_per_hour" to 1200.0, "image_url" to "https://images.unsplash.com/photo-1563299796-b729d0af54a5"),
            mapOf("name" to "Eclipse Stadium", "location" to "North Suburbs", "price_per_hour" to 2200.0, "image_url" to "https://images.unsplash.com/photo-1518063319789-7217e6706b04"),
            mapOf("name" to "Pro Pitch Arena", "location" to "South District", "price_per_hour" to 3000.0, "image_url" to "https://images.unsplash.com/photo-1587329310686-91414b8e3cb7"),
            mapOf("name" to "All-Star Turf", "location" to "City Outskirts", "price_per_hour" to 1000.0, "image_url" to "https://images.unsplash.com/photo-1580629905303-faaa03202631")
        )
        for (turf in turfs) {
            val json = JsonObject().apply {
                addProperty("name", turf["name"] as String)
                addProperty("location", turf["location"] as String)
                addProperty("price_per_hour", turf["price_per_hour"] as Double)
                addProperty("image_url", turf["image_url"] as String)
            }.toString()
            postAndGetId("turfs", json)
        }
    }

    fun getAllTurfs(): List<Turf> {
        val body = get("turfs", "select=*&order=id")
        val list = mutableListOf<Turf>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) {
                Log.e(TAG, "getAllTurfs FAILED to parse JSON array. Response body: $body")
                return list
            }
            val arr = element.asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                list.add(Turf(
                    id = o.safeLong("id"),
                    name = o.safeString("name"),
                    location = o.safeString("location"),
                    pricePerHour = o.safeDouble("price_per_hour"),
                    imageUrl = o.safeString("image_url"),
                    addedBy = o.safeLong("added_by")
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "Parse turfs: ${e.message}") }
        return list
    }

    fun addTurf(turf: Turf): Long {
        val json = JsonObject().apply {
            addProperty("name", turf.name)
            addProperty("location", turf.location)
            addProperty("price_per_hour", turf.pricePerHour)
            addProperty("image_url", turf.imageUrl)
            addProperty("added_by", turf.addedBy)
        }.toString()
        return postAndGetId("turfs", json)
    }

    fun deleteTurf(id: Long): Boolean {
        return delete("turfs", "id=eq.$id")
    }

    fun updateTurf(id: Long, name: String, location: String, pricePerHour: Double): Boolean {
        val json = JsonObject().apply {
            addProperty("name", name)
            addProperty("location", location)
            addProperty("price_per_hour", pricePerHour)
        }.toString()
        val urlString = "$SUPABASE_URL/rest/v1/turfs?id=eq.$id"
        return try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "PATCH"
            connection.setRequestProperty("apikey", SUPABASE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            connection.doOutput = true

            java.io.OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(json)
                writer.flush()
            }

            val responseCode = connection.responseCode
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "updateTurf failed: ${e.message}")
            false
        }
    }

    fun addBooking(booking: Booking): Long {
        val json = JsonObject().apply {
            addProperty("turf_id", booking.turfId)
            addProperty("date", booking.date)
            addProperty("time", booking.time)
            addProperty("user_id", booking.userId)
        }.toString()
        return postAndGetId("bookings", json)
    }

    // ========== TOURNAMENT ==========

    fun addTournament(tour: Tournament): Long {
        val json = JsonObject().apply {
            addProperty("name", tour.name)
            addProperty("location", tour.location)
            addProperty("start_date", tour.startDate)
            addProperty("end_date", tour.endDate)
            addProperty("entry_fee", tour.entryFee)
            addProperty("prize_pool", tour.prizePool)
            addProperty("organizer_phone", tour.organizerPhone)
            addProperty("overs", tour.overs)
            addProperty("ground_name", tour.groundName)
            addProperty("ball_type", tour.ballType)
            addProperty("powerplay_overs", tour.powerplayOvers)
            addProperty("format", tour.format)
            addProperty("fixtures_created", tour.fixturesCreated)
            addProperty("organizer_email", tour.organizerEmail)
        }.toString()
        return postAndGetId("tournaments", json)
    }

    fun getAllTournamentsWithStats(): List<Tournament> {
        val body = get("tournaments", "select=*,tournament_teams(count)&order=id.desc")
        return parseTournaments(body)
    }

    fun getAllTournaments(): List<Tournament> {
        val body = get("tournaments", "select=*&order=id.desc")
        return parseTournaments(body)
    }

    fun getTournamentById(id: Long): Tournament? {
        val body = get("tournaments", "select=*,tournament_teams(count)&id=eq.$id")
        val list = parseTournaments(body)
        return list.firstOrNull()
    }

    private fun parseTournaments(body: String): List<Tournament> {
        val list = mutableListOf<Tournament>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return list
            val arr = element.asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                val t = Tournament(
                    id = o.safeLong("id"),
                    name = o.safeString("name"),
                    location = o.safeString("location"),
                    startDate = o.safeString("start_date"),
                    endDate = o.safeString("end_date"),
                    entryFee = o.safeDouble("entry_fee"),
                    prizePool = o.safeString("prize_pool"),
                    organizerPhone = o.safeString("organizer_phone"),
                    overs = o.safeInt("overs", 20),
                    groundName = o.safeString("ground_name"),
                    ballType = o.safeString("ball_type", "Tennis"),
                    powerplayOvers = o.safeInt("powerplay_overs", 6),
                    format = o.safeString("format"),
                    fixturesCreated = o.safeBool("fixtures_created"),
                    organizerEmail = o.safeString("organizer_email")
                )
                // Parse nested count if available
                if (o.has("tournament_teams") && o.get("tournament_teams").isJsonArray) {
                    val teamsArr = o.getAsJsonArray("tournament_teams")
                    if (teamsArr.size() > 0) {
                        t.teamCount = teamsArr[0].asJsonObject.get("count").asInt
                    }
                }
                list.add(t)
            }
        } catch (e: Exception) { Log.e(TAG, "Parse tournaments: ${e.message}") }
        return list
    }

    fun updateTournament(tour: Tournament) {
        val json = JsonObject().apply {
            addProperty("format", tour.format)
            addProperty("fixtures_created", tour.fixturesCreated)
        }.toString()
        patch("tournaments", "id=eq.${tour.id}", json)
    }

    // ========== TOURNAMENT TEAMS ==========

    fun addTournamentTeam(tournamentId: Long, teamName: String): Long {
        val json = JsonObject().apply {
            addProperty("tournament_id", tournamentId)
            addProperty("team_name", teamName)
        }.toString()
        return postAndGetId("tournament_teams", json)
    }

    fun getTournamentTeams(tournamentId: Long): List<String> {
        val body = get("tournament_teams", "select=team_name&tournament_id=eq.$tournamentId")
        val teams = mutableListOf<String>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return teams
            val arr = element.asJsonArray
            for (el in arr) teams.add(el.asJsonObject.get("team_name").asString)
        } catch (e: Exception) { Log.e(TAG, "Parse tourney teams: ${e.message}") }
        return teams
    }

    fun getTournamentTeamIds(tournamentId: Long): List<Pair<Long, String>> {
        val body = get("tournament_teams", "select=id,team_name&tournament_id=eq.$tournamentId")
        val teams = mutableListOf<Pair<Long, String>>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return teams
            val arr = element.asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                teams.add(Pair(o.get("id").asLong, o.get("team_name").asString))
            }
        } catch (e: Exception) { Log.e(TAG, "Parse tourney team ids: ${e.message}") }
        return teams
    }

    fun getTournamentTeamCount(tournamentId: Long): Int {
        val body = get("tournament_teams", "select=id&tournament_id=eq.$tournamentId")
        return try {
            val element = safeParse(body)
            if (element != null && element.isJsonArray) element.asJsonArray.size() else 0
        } catch (e: Exception) { 0 }
    }

    fun getPlayersForTeam(teamName: String): List<Player> {
        val encoded = java.net.URLEncoder.encode(teamName, "UTF-8")
        val body = get("players", "team_name=eq.$encoded")
        return parsePlayers(body)
    }

    fun getTournamentPlayersByTeamName(tournamentId: Long, teamName: String): List<Player> {
        val teams = getTournamentTeamIds(tournamentId)

        fun clean(s: String) = s.replace(Regex("[^A-Za-z0-9]"), "").lowercase()
        val target = clean(teamName)

        // Implement super-fuzzy matching
        val teamPair = teams.firstOrNull { team ->
            val cleanedTeamName = clean(team.second)
            cleanedTeamName == target || cleanedTeamName.contains(target) || target.contains(cleanedTeamName)
        }
        val teamId = teamPair?.first ?: -1L
        
        if (teamId == -1L) {
            Log.e(TAG, "getTournamentPlayersByTeamName: Team '$teamName' (cleaned: $target) NOT FOUND in TID: $tournamentId. Available: ${teams.joinToString { it.second }}")
            return emptyList()
        }

        val details = getTournamentTeamPlayerDetails(teamId)
        Log.d(TAG, "getTournamentPlayersByTeamName: Found ${details.size} players for Team '$teamName' (ID: $teamId)")
        return details.map { (id, name, role) ->
            Player(id = id, name = name, role = role, phone = "", matchType = "", isWicketKeeper = false, isAvailable = true, skillRating = 0, expLevel = 0f, availabilityDate = "", matchTime = "")
        }
    }

    fun getTournamentPlayersByIds(ids: List<Long>): List<Player> {
        if (ids.isEmpty()) return emptyList()
        val filter = "id=in.(${ids.joinToString(",")})"
        val body = get("tournament_team_players", filter)
        val players = mutableListOf<Player>()
        try {
            val element = safeParse(body)
            if (element != null && element.isJsonArray) {
                val arr = element.asJsonArray
                for (el in arr) {
                    val o = el.asJsonObject
                    players.add(Player(
                        id = o.get("id").asLong,
                        name = o.get("player_name").asString,
                        role = if (o.has("role") && !o.get("role").isJsonNull) o.get("role").asString else "",
                        phone = "", matchType = "", isWicketKeeper = false, isAvailable = true, skillRating = 0, expLevel = 0f, availabilityDate = "", matchTime = ""
                    ))
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Parse tournament players by ids: ${e.message}") }
        return players
    }

    fun getPlayersForTeamByIds(teamName: String, ids: List<Long>): List<Player> {
        if (ids.isEmpty()) return emptyList()
        val idFilter = "id=in.(${ids.joinToString(",")})"
        val encodedTeam = java.net.URLEncoder.encode(teamName, "UTF-8")
        val body = get("players", "$idFilter&team_name=eq.$encodedTeam")
        return parsePlayers(body)
    }

    fun isTournamentTeamNameTaken(tournamentId: Long, teamName: String): Boolean {
        val encoded = java.net.URLEncoder.encode(teamName, "UTF-8")
        val body = get("tournament_teams", "select=id&tournament_id=eq.$tournamentId&team_name=eq.$encoded")
        return try {
            val element = safeParse(body)
            if (element != null && element.isJsonArray) element.asJsonArray.size() > 0 else false
        } catch (e: Exception) { false }
    }

    // ========== TOURNAMENT TEAM PLAYERS ==========

    fun addTournamentTeamPlayer(teamId: Long, playerName: String): Long {
        val json = JsonObject().apply {
            addProperty("tournament_team_id", teamId)
            addProperty("player_name", playerName)
        }.toString()
        return postAndGetId("tournament_team_players", json)
    }

    fun getTournamentTeamPlayers(teamId: Long): List<String> {
        val body = get("tournament_team_players", "select=player_name&tournament_team_id=eq.$teamId")
        val players = mutableListOf<String>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return players
            val arr = element.asJsonArray
            for (el in arr) players.add(el.asJsonObject.get("player_name").asString)
        } catch (e: Exception) { Log.e(TAG, "Parse team players: ${e.message}") }
        return players
    }

    fun getTournamentTeamPlayerCount(teamId: Long): Int {
        val body = get("tournament_team_players", "select=*&tournament_team_id=eq.$teamId")
        return try {
            val element = safeParse(body)
            if (element != null && element.isJsonArray) element.asJsonArray.size() else 0
        } catch (e: Exception) { 0 }
    }

    fun removeTournamentTeamPlayer(teamId: Long, playerName: String) {
        val encoded = java.net.URLEncoder.encode(playerName, "UTF-8")
        delete("tournament_team_players", "tournament_team_id=eq.$teamId&player_name=eq.$encoded")
    }

    fun getTournamentTeamPlayerDetails(teamId: Long): List<Triple<Long, String, String>> {
        val body = get("tournament_team_players", "select=*&tournament_team_id=eq.$teamId")
        val players = mutableListOf<Triple<Long, String, String>>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return players
            val arr = element.asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                val role = if (o.has("role") && !o.get("role").isJsonNull) o.get("role").asString else ""
                players.add(Triple(o.get("id").asLong, o.get("player_name").asString, role))
            }
        } catch (e: Exception) { Log.e(TAG, "Parse team player details: ${e.message}") }
        return players
    }

    fun updatePlayerRole(playerId: Long, role: String) {
        val json = JsonObject().apply { addProperty("role", role) }.toString()
        patch("tournament_team_players", "id=eq.$playerId", json)
    }

    fun clearTeamRoles(teamId: Long, roleToClear: String) {
        // Find existing player with this role and clear it
        val players = getTournamentTeamPlayerDetails(teamId)
        for (p in players) {
            if (p.third == roleToClear) {
                updatePlayerRole(p.first, "")
            }
        }
    }

    fun updateTournamentTeamName(teamId: Long, newName: String) {
        val json = JsonObject().apply {
            addProperty("team_name", newName)
        }.toString()
        patch("tournament_teams", "id=eq.$teamId", json)
    }

    fun updateTournamentTeamPlayerName(playerId: Long, newName: String) {
        val json = JsonObject().apply {
            addProperty("player_name", newName)
        }.toString()
        patch("tournament_team_players", "id=eq.$playerId", json)
    }

    fun clearTournamentTeamPlayers(teamId: Long) {
        delete("tournament_team_players", "tournament_team_id=eq.$teamId")
    }

    fun isTournamentCompleted(tournamentId: Long): Boolean {
        val fixtures = getFixturesForTournament(tournamentId)
        if (fixtures.isEmpty()) return false
        val finalFixture = fixtures.find { it.round == "Final" }
        return finalFixture?.status == "Completed"
    }

    // ========== FIXTURES ==========

    fun addFixture(fixture: Fixture): Long {
        val json = JsonObject().apply {
            addProperty("tournament_id", fixture.tournamentId)
            addProperty("team1_name", fixture.team1Name)
            addProperty("team2_name", fixture.team2Name)
            addProperty("match_number", fixture.matchNumber)
            addProperty("round", fixture.round)
            if (fixture.matchId > 0) addProperty("match_id", fixture.matchId)
            addProperty("status", fixture.status)
        }.toString()
        return postAndGetId("fixtures", json)
    }

    fun getFixturesForTournament(tournamentId: Long): List<Fixture> {
        val body = get("fixtures", "select=*&tournament_id=eq.$tournamentId&order=match_number")
        val list = mutableListOf<Fixture>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return list
            val arr = element.asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                list.add(Fixture(
                    id = o.safeLong("id"),
                    tournamentId = o.safeLong("tournament_id"),
                    team1Name = o.safeString("team1_name"),
                    team2Name = o.safeString("team2_name"),
                    matchNumber = o.safeInt("match_number"),
                    round = o.safeString("round"),
                    matchId = o.safeLong("match_id"),
                    status = o.safeString("status", "Upcoming")
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "Parse fixtures: ${e.message}") }
        return list
    }

    fun getRecentFixtures(limit: Int = 5): List<Fixture> {
        val body = get("fixtures", "select=*&order=id.desc&limit=$limit")
        val list = mutableListOf<Fixture>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return list
            val arr = element.asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                list.add(Fixture(
                    id = o.safeLong("id"),
                    tournamentId = o.safeLong("tournament_id"),
                    team1Name = o.safeString("team1_name"),
                    team2Name = o.safeString("team2_name"),
                    matchNumber = o.safeInt("match_number"),
                    round = o.safeString("round"),
                    matchId = o.safeLong("match_id"),
                    status = o.safeString("status", "Upcoming")
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "Parse recent fixtures: ${e.message}") }
        return list
    }

    fun updateFixtureMatchId(fixtureId: Long, matchId: Long, status: String) {
        val json = JsonObject().apply {
            addProperty("match_id", matchId)
            addProperty("status", status)
        }.toString()
        patch("fixtures", "id=eq.$fixtureId", json)
    }

    fun updateFixtureStatus(fixtureId: Long, status: String) {
        val json = JsonObject().apply {
            addProperty("status", status)
        }.toString()
        patch("fixtures", "id=eq.$fixtureId", json)
    }

    fun updateFixture(fixture: Fixture) {
        val json = JsonObject().apply {
            addProperty("team1_name", fixture.team1Name)
            addProperty("team2_name", fixture.team2Name)
            addProperty("match_number", fixture.matchNumber)
            addProperty("round", fixture.round)
            if (fixture.matchId > 0) addProperty("match_id", fixture.matchId)
            addProperty("status", fixture.status)
        }.toString()
        patch("fixtures", "id=eq.${fixture.id}", json)
    }

    fun deleteFixturesForTournament(tournamentId: Long) {
        delete("fixtures", "tournament_id=eq.$tournamentId")
    }

    fun resetTournamentFixtures(tournamentId: Long) {
        // Delete all fixtures and associated matches for a tournament
        delete("matches", "tournament_id=eq.$tournamentId")
        delete("fixtures", "tournament_id=eq.$tournamentId")
        // Reset fixturesCreated flag
        val json = JsonObject().apply {
            addProperty("fixtures_created", false)
        }.toString()
        patch("tournaments", "id=eq.$tournamentId", json)
    }

    // ========== MATCH ==========

    fun addMatch(match: Match): Long {
        val json = JsonObject().apply {
            addProperty("team1_name", match.team1Name)
            addProperty("team2_name", match.team2Name)
            addProperty("match_status", match.matchStatus)
            addProperty("winner", match.winner)
            if (match.tournamentId > 0) addProperty("tournament_id", match.tournamentId)
            if (match.fixtureId > 0) addProperty("fixture_id", match.fixtureId)
        }.toString()
        return postAndGetId("matches", json)
    }

    fun getAllMatches(): List<Match> {
        val body = get("matches", "select=*&order=id.desc")
        return parseMatches(body)
    }

    fun getMatchById(matchId: Long): Match? {
        val body = get("matches", "select=*&id=eq.$matchId")
        return parseMatches(body).firstOrNull()
    }

    fun getMatchesByFixtureId(fixtureId: Long): List<Match> {
        val json = get("matches", "fixture_id=eq.$fixtureId")
        return parseMatches(json)
    }

    fun getMatchesForTournament(tournamentId: Long): List<Match> {
        val body = get("matches", "select=*&tournament_id=eq.$tournamentId")
        return parseMatches(body)
    }

    private fun parseMatches(body: String): List<Match> {
        val list = mutableListOf<Match>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return list
            val arr = element.asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                list.add(Match(
                    id = o.safeLong("id"),
                    team1Id = o.safeLong("team1_id"),
                    team2Id = o.safeLong("team2_id"),
                    team1Name = o.safeString("team1_name"),
                    team2Name = o.safeString("team2_name"),
                    team1Runs = o.safeInt("team1_runs"),
                    team1Wickets = o.safeInt("team1_wickets"),
                    team1Balls = o.safeInt("team1_balls"),
                    team2Runs = o.safeInt("team2_runs"),
                    team2Wickets = o.safeInt("team2_wickets"),
                    team2Balls = o.safeInt("team2_balls"),
                    team1Extras = o.safeInt("team1_extras"),
                    team2Extras = o.safeInt("team2_extras"),
                    matchStatus = o.safeString("match_status", "Ongoing"),
                    winner = o.safeString("winner"),
                    currentBatsman1 = o.safeString("current_batsman1"),
                    currentBatsman2 = o.safeString("current_batsman2"),
                    batsman1Runs = o.safeInt("batsman1_runs"),
                    batsman1Balls = o.safeInt("batsman1_balls"),
                    batsman1Fours = o.safeInt("batsman1_fours"),
                    batsman1Sixes = o.safeInt("batsman1_sixes"),
                    batsman2Runs = o.safeInt("batsman2_runs"),
                    batsman2Balls = o.safeInt("batsman2_balls"),
                    batsman2Fours = o.safeInt("batsman2_fours"),
                    batsman2Sixes = o.safeInt("batsman2_sixes"),
                    currentBowler = o.safeString("current_bowler"),
                    bowlerOvers = o.safeInt("bowler_overs"),
                    bowlerMaidens = o.safeInt("bowler_maidens"),
                    bowlerRuns = o.safeInt("bowler_runs"),
                    bowlerWickets = o.safeInt("bowler_wickets"),
                    lastBowlerName = o.safeString("last_bowler_name"),
                    tournamentId = o.safeLong("tournament_id"),
                    fixtureId = o.safeLong("fixture_id")
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "Parse matches: ${e.message}") }
        return list
    }

    fun updateMatch(match: Match): Int {
        val json = JsonObject().apply {
            addProperty("team1_name", match.team1Name)
            addProperty("team2_name", match.team2Name)
            addProperty("team1_runs", match.team1Runs)
            addProperty("team1_wickets", match.team1Wickets)
            addProperty("team1_balls", match.team1Balls)
            addProperty("team2_runs", match.team2Runs)
            addProperty("team2_wickets", match.team2Wickets)
            addProperty("team2_balls", match.team2Balls)
            addProperty("team1_extras", match.team1Extras)
            addProperty("team2_extras", match.team2Extras)
            addProperty("match_status", match.matchStatus)
            addProperty("winner", match.winner)
            addProperty("current_batsman1", match.currentBatsman1)
            addProperty("current_batsman2", match.currentBatsman2)
            addProperty("batsman1_runs", match.batsman1Runs)
            addProperty("batsman1_balls", match.batsman1Balls)
            addProperty("batsman1_fours", match.batsman1Fours)
            addProperty("batsman1_sixes", match.batsman1Sixes)
            addProperty("batsman2_runs", match.batsman2Runs)
            addProperty("batsman2_balls", match.batsman2Balls)
            addProperty("batsman2_fours", match.batsman2Fours)
            addProperty("batsman2_sixes", match.batsman2Sixes)
            addProperty("current_bowler", match.currentBowler)
            addProperty("bowler_overs", match.bowlerOvers)
            addProperty("bowler_maidens", match.bowlerMaidens)
            addProperty("bowler_runs", match.bowlerRuns)
            addProperty("bowler_wickets", match.bowlerWickets)
            addProperty("last_bowler_name", match.lastBowlerName)
            if (match.tournamentId > 0) addProperty("tournament_id", match.tournamentId)
            if (match.fixtureId > 0) addProperty("fixture_id", match.fixtureId)
        }.toString()
        return if (patch("matches", "id=eq.${match.id}", json)) 1 else 0
    }

    // ========== TOURNAMENT DELETE ==========

    fun deleteTournament(tournamentId: Long): Boolean {
        try {
            // Get team IDs to delete players
            val teamIds = getTournamentTeamIds(tournamentId)
            for ((teamId, _) in teamIds) {
                delete("tournament_team_players", "tournament_team_id=eq.$teamId")
            }
            
            // Delete teams
            delete("tournament_teams", "tournament_id=eq.$tournamentId")

            // Delete matches linked to this tournament
            delete("matches", "tournament_id=eq.$tournamentId")

            // Delete fixtures
            delete("fixtures", "tournament_id=eq.$tournamentId")

            // Delete tournament
            return delete("tournaments", "id=eq.$tournamentId")
        } catch (e: Exception) {
            Log.e(TAG, "Delete tournament: ${e.message}")
            return false
        }
    }
    // ========== REQUESTS ==========

    fun getAllRequests(): List<com.example.crickzy.models.Request> {
        val body = get("requests", "select=*&order=id.desc")
        return parseRequests(body)
    }

    fun updateRequestStatus(requestId: Long, status: String): Boolean {
        val json = JsonObject().apply {
            addProperty("status", status)
        }.toString()
        return patch("requests", "id=eq.$requestId", json)
    }

    private fun parseRequests(body: String): List<com.example.crickzy.models.Request> {
        val list = mutableListOf<com.example.crickzy.models.Request>()
        try {
            val element = safeParse(body)
            if (element == null || !element.isJsonArray) return list
            val arr = element.asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                list.add(com.example.crickzy.models.Request(
                    id = o.safeLong("id"),
                    playerId = o.safeLong("player_id"),
                    teamId = o.safeLong("team_id"),
                    playerName = o.safeString("player_name", "Unknown Player"),
                    teamName = o.safeString("team_name", "Unknown Team"),
                    status = o.safeString("status", "Pending")
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "Parse requests: ${e.message}") }
        return list
    }
}
