package com.example.crickzy.tournament

// ---------------------------------------------------------------------------
// Knockout Data Models
// ---------------------------------------------------------------------------

enum class MatchStatus { SCHEDULED, BYE_ADVANCED, COMPLETED }

data class KnockoutMatch(
    val matchId: Int,
    val roundNumber: Int,
    val home: Team?,             // null = "winner of match X" placeholder
    val away: Team?,             // null = "winner of match X" placeholder
    val homeSourceMatchId: Int?, // which earlier match feeds this slot
    val awaySourceMatchId: Int?,
    val winner: Team? = null,
    val status: MatchStatus = MatchStatus.SCHEDULED
)

data class KnockoutRound(
    val roundNumber: Int,
    val roundName: String,       // "Round of 16", "Quarter-finals", etc.
    val matches: List<KnockoutMatch>
)

data class KnockoutBracket(
    val teams: List<Team>,
    val rounds: List<KnockoutRound>,
    val champion: Team? = null
)

// ---------------------------------------------------------------------------
// KnockoutFixtureGenerator
// ---------------------------------------------------------------------------

/**
 * Pure, unit-testable generator for Single Elimination (Knockout) brackets.
 *
 * ## Seeding and Byes
 * If n is not a power of 2, the bracket is padded to the next power of 2.
 * The top seeds (index 0, 1, etc.) receive the byes in round 1.
 * Standard single-elimination seeding separates top seeds so they meet later.
 */
class KnockoutFixtureGenerator {

    /**
     * Generates a knockout bracket.
     * @param teams List of participating teams (2 to 64).
     * @param randomSeed If true, teams are shuffled. If false, the input order is treated as seed order (0 = top seed).
     */
    fun generateKnockoutBracket(teams: List<Team>, randomSeed: Boolean): KnockoutBracket {
        require(teams.size in 2..64) { "Team count must be between 2 and 64" }

        val sortedTeams = if (randomSeed) teams.shuffled() else teams.toList()

        var nextPowerOfTwo = 1
        while (nextPowerOfTwo < sortedTeams.size) {
            nextPowerOfTwo *= 2
        }

        val seedOrder = getSeedOrder(nextPowerOfTwo)

        val slots = arrayOfNulls<Team>(nextPowerOfTwo)
        for (i in sortedTeams.indices) {
            val position = seedOrder.indexOf(i)
            slots[position] = sortedTeams[i]
        }

        var matchIdCounter = 1
        val rounds = mutableListOf<KnockoutRound>()

        // --- Round 1 ---
        val round1Matches = mutableListOf<KnockoutMatch>()
        for (i in 0 until nextPowerOfTwo step 2) {
            val home = slots[i]
            val away = slots[i + 1]
            val status = if (home == null || away == null) MatchStatus.BYE_ADVANCED else MatchStatus.SCHEDULED
            val winner = if (home == null) away else if (away == null) home else null
            
            round1Matches.add(
                KnockoutMatch(
                    matchId = matchIdCounter++,
                    roundNumber = 1,
                    home = home,
                    away = away,
                    homeSourceMatchId = null,
                    awaySourceMatchId = null,
                    winner = winner,
                    status = status
                )
            )
        }
        rounds.add(KnockoutRound(1, getRoundName(nextPowerOfTwo), round1Matches))

        // --- Subsequent Rounds ---
        var prevRoundMatches = round1Matches
        var currentRoundNumber = 2
        var currentMatchesCount = nextPowerOfTwo / 4

        while (currentMatchesCount >= 1) {
            val currentRoundMatches = mutableListOf<KnockoutMatch>()
            for (i in 0 until currentMatchesCount) {
                val homeMatch = prevRoundMatches[i * 2]
                val awayMatch = prevRoundMatches[i * 2 + 1]

                // If a previous match was a BYE, the winner automatically forwards to this round.
                val homeTeam = if (homeMatch.status == MatchStatus.BYE_ADVANCED) homeMatch.winner else null
                val awayTeam = if (awayMatch.status == MatchStatus.BYE_ADVANCED) awayMatch.winner else null

                // It's possible (though rare in standard seedings) that BOTH forwarded teams are BYEs, 
                // but standard brackets prevent two byes from playing each other in Round 1.
                currentRoundMatches.add(
                    KnockoutMatch(
                        matchId = matchIdCounter++,
                        roundNumber = currentRoundNumber,
                        home = homeTeam,
                        away = awayTeam,
                        homeSourceMatchId = homeMatch.matchId,
                        awaySourceMatchId = awayMatch.matchId,
                        winner = null,
                        status = MatchStatus.SCHEDULED
                    )
                )
            }
            rounds.add(KnockoutRound(currentRoundNumber, getRoundName(currentMatchesCount * 2), currentRoundMatches))
            prevRoundMatches = currentRoundMatches
            currentMatchesCount /= 2
            currentRoundNumber++
        }

        return KnockoutBracket(teams = sortedTeams, rounds = rounds)
    }

    /**
     * Returns standard single-elimination seed matchups.
     * E.g. for 4: [0, 3, 1, 2] -> 1v4, 2v3
     */
    private fun getSeedOrder(size: Int): List<Int> {
        var order = listOf(0)
        var currentSize = 1
        while (currentSize < size) {
            currentSize *= 2
            val nextOrder = mutableListOf<Int>()
            for (i in order) {
                nextOrder.add(i)
                nextOrder.add(currentSize - 1 - i)
            }
            order = nextOrder
        }
        return order
    }

    private fun getRoundName(teamsInRound: Int): String {
        return when (teamsInRound) {
            2 -> "Final"
            4 -> "Semi-finals"
            8 -> "Quarter-finals"
            16 -> "Round of 16"
            32 -> "Round of 32"
            64 -> "Round of 64"
            else -> "Round of $teamsInRound"
        }
    }
}
