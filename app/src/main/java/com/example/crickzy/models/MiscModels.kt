package com.example.crickzy.models

data class Turf(
    var id: Long = -1,
    var name: String,
    var location: String,
    var pricePerHour: Double,
    var imageUrl: String = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e",
    var addedBy: Long = -1
)

data class Booking(
    var id: Long = -1,
    var turfId: Long,
    var date: String,
    var time: String,
    var userId: Long = -1
)

data class BatsmanInnings(
    var name: String,
    var runs: Int = 0,
    var ballsFaced: Int = 0,
    var fours: Int = 0,
    var sixes: Int = 0,
    var isOnStrike: Boolean = false,
    var isOut: Boolean = false,
    var dismissalInfo: String = ""
) {
    fun getStrikeRate(): String {
        if (ballsFaced == 0) return "0.00"
        return String.format("%.2f", runs * 100.0 / ballsFaced)
    }
}

data class BowlerInnings(
    var name: String,
    var ballsBowled: Int = 0,
    var maidens: Int = 0,
    var runsConceded: Int = 0,
    var wickets: Int = 0,
    var currentOverRuns: Int = 0,
    var currentOverLegalBalls: Int = 0
) {
    fun getOversDisplay(): String {
        val overs = ballsBowled / 6
        val balls = ballsBowled % 6
        return "$overs.$balls"
    }

    fun getEconomy(): String {
        if (ballsBowled == 0) return "0.00"
        return String.format("%.2f", runsConceded * 6.0 / ballsBowled)
    }
}

data class Match(
    var id: Long = -1,
    var team1Id: Long = -1,
    var team2Id: Long = -1,
    var team1Name: String,
    var team2Name: String,
    var team1Runs: Int = 0,
    var team1Wickets: Int = 0,
    var team1Balls: Int = 0,
    var team2Runs: Int = 0,
    var team2Wickets: Int = 0,
    var team2Balls: Int = 0,
    var team1Extras: Int = 0,
    var team2Extras: Int = 0,
    var matchStatus: String = "Ongoing",
    var winner: String = "",
    var currentBatsman1: String = "",
    var currentBatsman2: String = "",
    var batsman1Runs: Int = 0,
    var batsman1Balls: Int = 0,
    var batsman1Fours: Int = 0,
    var batsman1Sixes: Int = 0,
    var batsman2Runs: Int = 0,
    var batsman2Balls: Int = 0,
    var batsman2Fours: Int = 0,
    var batsman2Sixes: Int = 0,
    var currentBowler: String = "",
    var bowlerOvers: Int = 0,
    var bowlerMaidens: Int = 0,
    var bowlerRuns: Int = 0,
    var bowlerWickets: Int = 0,
    var lastBowlerName: String = "",
    var tournamentId: Long = -1,
    var fixtureId: Long = -1
) {
    fun getTeam1OversDisplay(): String {
        val overs = team1Balls / 6
        val balls = team1Balls % 6
        return "$overs.$balls"
    }

    fun getTeam2OversDisplay(): String {
        val overs = team2Balls / 6
        val balls = team2Balls % 6
        return "$overs.$balls"
    }

    fun getTeam1RunRate(): String {
        if (team1Balls == 0) return "0.00"
        val rr = (team1Runs.toDouble() / team1Balls) * 6
        return String.format("%.2f", rr)
    }

    fun getTeam2RunRate(): String {
        if (team2Balls == 0) return "0.00"
        val rr = (team2Runs.toDouble() / team2Balls) * 6
        return String.format("%.2f", rr)
    }

    fun getBatsman1SR(): String {
        if (batsman1Balls == 0) return "0.00"
        return String.format("%.2f", batsman1Runs * 100.0 / batsman1Balls)
    }

    fun getBatsman2SR(): String {
        if (batsman2Balls == 0) return "0.00"
        return String.format("%.2f", batsman2Runs * 100.0 / batsman2Balls)
    }

    fun getBowlerOversDisplay(): String {
        val overs = bowlerOvers / 6
        val balls = bowlerOvers % 6
        return "$overs.$balls"
    }

    fun getBowlerEconomy(): String {
        if (bowlerOvers == 0) return "0.00"
        return String.format("%.2f", bowlerRuns * 6.0 / bowlerOvers)
    }
}

data class Request(
    var id: Long = -1,
    var playerId: Long,
    var teamId: Long,
    var playerName: String = "",
    var teamName: String = "",
    var status: String = "Pending"
)

data class Tournament(
    var id: Long = -1,
    var name: String,
    var location: String,
    var startDate: String,
    var endDate: String,
    var entryFee: Double,
    var prizePool: String,
    var organizerPhone: String,
    var overs: Int = 20,
    var groundName: String = "",
    var ballType: String = "Tennis",
    var powerplayOvers: Int = 6,
    var format: String = "",        // "Knockout" or "League"
    var fixturesCreated: Boolean = false,
    var organizerEmail: String = "",
    var teamCount: Int = 0          // Added for optimization
)

data class Fixture(
    var id: Long = -1,
    var tournamentId: Long,
    var team1Name: String,
    var team2Name: String,
    var matchNumber: Int = 0,
    var round: String = "",         // "Round 1", "Semi-Final", "Final" for knockout; "League" for league
    var matchId: Long = -1,         // linked match id once started
    var status: String = "Upcoming" // "Upcoming", "Live", "Completed"
)

data class PointsTableEntry(
    var teamName: String,
    var played: Int = 0,
    var won: Int = 0,
    var lost: Int = 0,
    var tied: Int = 0,
    var points: Int = 0,
    var totalRunsScored: Int = 0,
    var totalBallsFaced: Int = 0,
    var totalRunsConceded: Int = 0,
    var totalBallsBowled: Int = 0,
    var nrr: Double = 0.0
)

/**
 * Represents a snapshot of scoring state for undo functionality.
 */
data class ScoringSnapshot(
    var team1Runs: Int,
    var team1Wickets: Int,
    var team1Balls: Int,
    var team1Extras: Int,
    var team2Runs: Int,
    var team2Wickets: Int,
    var team2Balls: Int,
    var team2Extras: Int,
    var isTeam1Batting: Boolean,
    var strikerName: String,
    var strikerRuns: Int,
    var strikerBalls: Int,
    var strikerFours: Int,
    var strikerSixes: Int,
    var nonStrikerName: String,
    var nonStrikerRuns: Int,
    var nonStrikerBalls: Int,
    var nonStrikerFours: Int,
    var nonStrikerSixes: Int,
    var bowlerName: String,
    var bowlerBalls: Int,
    var bowlerMaidens: Int,
    var bowlerRunsConceded: Int,
    var bowlerWickets: Int,
    var bowlerCurrentOverRuns: Int,
    var bowlerCurrentOverLegalBalls: Int,
    var overBalls: List<String>,
    var lastBowlerName: String = ""
)
