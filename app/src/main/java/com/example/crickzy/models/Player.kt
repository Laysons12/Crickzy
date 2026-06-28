package com.example.crickzy.models

data class Player(
    var id: Long = -1,
    var name: String,
    var phone: String,
    var whatsapp: String = "",
    var role: String,
    var matchType: String,
    var isWicketKeeper: Boolean,
    var isAvailable: Boolean,
    var skillRating: Int, // 1 to 100
    var expLevel: Float, // 1.0 to 5.0
    var availabilityDate: String,
    var matchTime: String,
    var profileImageUri: String? = null,
    var ballType: String = "Tennis",
    var area: String = "",
    var email: String = "",
    var addedBy: Long = -1
)
