package com.example.crickzy.models

data class Team(
    var id: Long = -1,
    var name: String,
    var location: String,
    var requiredRole: String,
    var budgetProgress: Int, // 0 to 100
    var matchDate: String,
    var matchTime: String,
    var needsPlayers: Boolean,
    var ballType: String = "",
    var area: String = "",
    var phone: String = "",
    var whatsapp: String = "",
    var addedBy: Long = -1
)
