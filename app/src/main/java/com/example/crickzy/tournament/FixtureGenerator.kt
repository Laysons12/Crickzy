package com.example.crickzy.tournament

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

data class Team(val id: Int, val name: String)

data class Match(val home: Team, val away: Team)

data class Round(
    val roundNumber: Int,
    val matches: List<Match>,
    val byeTeam: Team? = null
)

data class Tournament(
    val teams: List<Team>,
    val doubleRoundRobin: Boolean,
    val rounds: List<Round>
)

// ---------------------------------------------------------------------------
// Points table models
// ---------------------------------------------------------------------------

/**
 * Result of a single match from one team's perspective.
 */
enum class MatchResult { WIN, DRAW, LOSS }

/**
 * Mutable accumulator used while building the standings.
 */
data class TeamStanding(
    val team: Team,
    var played: Int = 0,
    var wins: Int = 0,
    var draws: Int = 0,
    var losses: Int = 0,
    var points: Int = 0
) {
    /** Derived: goal-difference equivalent for display ordering */
    val winPercentage: Double
        get() = if (played == 0) 0.0 else wins.toDouble() / played
}

/**
 * A fully computed, immutable standings row.
 */
data class StandingRow(
    val position: Int,
    val team: Team,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val points: Int
)

// ---------------------------------------------------------------------------
// Points system (WIN = 2, DRAW = 1, LOSS = 0)
// ---------------------------------------------------------------------------

object PointsSystem {
    const val WIN_POINTS = 2
    const val DRAW_POINTS = 1
    const val LOSS_POINTS = 0
}

// ---------------------------------------------------------------------------
// FixtureGenerator — circle-method round-robin scheduler
// ---------------------------------------------------------------------------

/**
 * Pure, Android-free class that generates round-robin fixtures and maintains
 * a live points table.
 *
 * ## Circle Method (even n)
 * 1. Pin `arr[0]`; rotate the remaining n-1 elements right by one each round.
 * 2. For round r, pair arr[i] vs arr[n-1-i] for i in 0 until n/2.
 * 3. Repeat for n-1 rounds to guarantee each team plays every other team
 *    exactly once.
 *
 * If n is odd, a virtual "BYE" team is appended first so the even-n algorithm
 * can run unchanged; any match involving BYE becomes a bye for the real team.
 */
class FixtureGenerator {

    /** Sentinel used to represent a bye slot in the circle algorithm. */
    private val BYE = Team(id = -1, name = "BYE")

    // -----------------------------------------------------------------------
    // Fixture generation
    // -----------------------------------------------------------------------

    /**
     * Generate a full round-robin fixture list.
     *
     * @param teams           The participating teams (≥ 2, ≤ 20).
     * @param doubleRoundRobin If true, a return leg (home/away swapped) is
     *                         appended after the first set of rounds.
     * @return A [Tournament] containing all rounds.
     * @throws IllegalArgumentException for invalid team counts.
     */
    fun generateFixtures(teams: List<Team>, doubleRoundRobin: Boolean): Tournament {
        require(teams.size >= 2) { "At least 2 teams are required." }
        require(teams.size <= 20) { "Maximum 20 teams are supported." }

        // Pad to even length with a BYE if the team count is odd.
        val arr = if (teams.size % 2 == 0) teams.toMutableList()
                  else (teams + BYE).toMutableList()

        val total     = arr.size
        val half      = total / 2
        val numRounds = total - 1

        val rounds = mutableListOf<Round>()

        // --- Single round-robin ---
        for (round in 0 until numRounds) {
            val matches   = mutableListOf<Match>()
            var byeTeam: Team? = null

            for (i in 0 until half) {
                val home = arr[i]
                val away = arr[total - 1 - i]
                when {
                    home == BYE -> byeTeam = away
                    away == BYE -> byeTeam = home
                    else        -> matches.add(Match(home, away))
                }
            }

            rounds.add(Round(roundNumber = round + 1, matches = matches, byeTeam = byeTeam))

            // Rotate: keep arr[0] fixed; shift arr[1..total-1] right by one.
            // i.e. last element moves to index 1, everything else moves right.
            val last = arr.removeAt(total - 1)
            arr.add(1, last)
        }

        // --- Double round-robin: mirror with home/away swapped ---
        if (doubleRoundRobin) {
            val returnRounds = rounds.mapIndexed { index, r ->
                Round(
                    roundNumber = numRounds + index + 1,
                    matches     = r.matches.map { Match(home = it.away, away = it.home) },
                    byeTeam     = r.byeTeam
                )
            }
            rounds.addAll(returnRounds)
        }

        return Tournament(teams = teams, doubleRoundRobin = doubleRoundRobin, rounds = rounds)
    }

    // -----------------------------------------------------------------------
    // Points table computation
    // -----------------------------------------------------------------------

    /**
     * Compute the current standings from a map of match results.
     *
     * Only teams listed in [tournament] are included; unplayed matches
     * (absent from [results]) are simply ignored.
     *
     * @param tournament  The tournament whose rounds are being tracked.
     * @param results     Map from [Match] to a pair (homeScore, awayScore).
     * @return Sorted list of [StandingRow], ranked by points descending,
     *         then wins descending (simple tiebreak).
     */
    fun computeStandings(
        tournament: Tournament,
        results: Map<Match, Pair<Int, Int>>
    ): List<StandingRow> {
        // Initialise a standings entry for every real team.
        val standingsMap = tournament.teams.associate { team ->
            team.id to TeamStanding(team)
        }

        for ((match, score) in results) {
            val (homeScore, awayScore) = score

            val homeStanding = standingsMap[match.home.id] ?: continue
            val awayStanding = standingsMap[match.away.id] ?: continue

            homeStanding.played++
            awayStanding.played++

            when {
                homeScore > awayScore -> {
                    // Home win
                    homeStanding.wins++;   homeStanding.points += PointsSystem.WIN_POINTS
                    awayStanding.losses++; awayStanding.points += PointsSystem.LOSS_POINTS
                }
                awayScore > homeScore -> {
                    // Away win
                    awayStanding.wins++;   awayStanding.points += PointsSystem.WIN_POINTS
                    homeStanding.losses++; homeStanding.points += PointsSystem.LOSS_POINTS
                }
                else -> {
                    // Draw
                    homeStanding.draws++; homeStanding.points += PointsSystem.DRAW_POINTS
                    awayStanding.draws++; awayStanding.points += PointsSystem.DRAW_POINTS
                }
            }
        }

        // Sort: points ↓, then wins ↓, then name ↑ (stable tiebreak)
        return standingsMap.values
            .sortedWith(compareByDescending<TeamStanding> { it.points }
                .thenByDescending { it.wins }
                .thenBy { it.team.name })
            .mapIndexed { index, s ->
                StandingRow(
                    position = index + 1,
                    team     = s.team,
                    played   = s.played,
                    wins     = s.wins,
                    draws    = s.draws,
                    losses   = s.losses,
                    points   = s.points
                )
            }
    }
}
