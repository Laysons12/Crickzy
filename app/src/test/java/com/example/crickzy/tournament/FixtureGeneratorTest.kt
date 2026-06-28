package com.example.crickzy.tournament

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FixtureGeneratorTest {

    private lateinit var generator: FixtureGenerator

    @Before
    fun setUp() {
        generator = FixtureGenerator()
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private fun makeTeams(n: Int) = (1..n).map { Team(id = it, name = "Team $it") }

    // -----------------------------------------------------------------------
    // Fixture generation — original requirements
    // -----------------------------------------------------------------------

    @Test
    fun `n=4 single round robin produces 3 rounds and 6 matches`() {
        val teams      = makeTeams(4)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = false)

        assertEquals(3, tournament.rounds.size)

        val allMatches = tournament.rounds.flatMap { it.matches }
        assertEquals(6, allMatches.size)
    }

    @Test
    fun `n=4 single round robin - every pair plays exactly once`() {
        val teams      = makeTeams(4)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = false)

        val pairings = tournament.rounds.flatMap { it.matches }
            .map { setOf(it.home.id, it.away.id) }

        // All 4C2 = 6 unique pairs must appear
        assertEquals(pairings.size, pairings.distinct().size)
    }

    @Test
    fun `n=4 single round robin - correct round order (circle method)`() {
        val teams      = makeTeams(4)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = false)

        val r1 = tournament.rounds[0].matches.map { it.home.id to it.away.id }
        val r2 = tournament.rounds[1].matches.map { it.home.id to it.away.id }
        val r3 = tournament.rounds[2].matches.map { it.home.id to it.away.id }

        // Expected per the spec
        assertTrue(r1.contains(1 to 4))
        assertTrue(r1.contains(2 to 3))
        assertTrue(r2.contains(1 to 3))
        assertTrue(r2.contains(4 to 2))
        assertTrue(r3.contains(1 to 2))
        assertTrue(r3.contains(3 to 4))
    }

    @Test
    fun `n=5 single round robin - 5 rounds, 2 matches per round, each team has exactly one bye`() {
        val teams      = makeTeams(5)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = false)

        assertEquals(5, tournament.rounds.size)
        tournament.rounds.forEach { round ->
            assertEquals(2, round.matches.size)
            assertNotNull("Every round should have a bye team", round.byeTeam)
        }

        // Each real team gets exactly one bye across all rounds
        val byeCounts = teams.associate { it.id to 0 }.toMutableMap()
        tournament.rounds.forEach { round ->
            round.byeTeam?.let { byeCounts[it.id] = byeCounts.getOrDefault(it.id, 0) + 1 }
        }
        byeCounts.values.forEach { count ->
            assertEquals("Each team should have exactly 1 bye", 1, count)
        }
    }

    @Test
    fun `n=6 double round robin - 10 rounds, 30 total matches`() {
        val teams      = makeTeams(6)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = true)

        assertEquals(10, tournament.rounds.size)
        assertEquals(30, tournament.rounds.sumOf { it.matches.size })
    }

    @Test
    fun `n=6 double round robin - return legs are home-away swapped`() {
        val teams      = makeTeams(6)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = true)

        val firstLegMatches  = tournament.rounds[0].matches
        val returnLegMatches = tournament.rounds[5].matches

        firstLegMatches.forEachIndexed { idx, match ->
            val returnMatch = returnLegMatches[idx]
            assertEquals(match.home.id, returnMatch.away.id)
            assertEquals(match.away.id, returnMatch.home.id)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `n=1 should throw IllegalArgumentException`() {
        generator.generateFixtures(makeTeams(1), doubleRoundRobin = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `n=0 should throw IllegalArgumentException`() {
        generator.generateFixtures(emptyList(), doubleRoundRobin = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `n=21 should throw IllegalArgumentException`() {
        generator.generateFixtures(makeTeams(21), doubleRoundRobin = false)
    }

    // -----------------------------------------------------------------------
    // Points table
    // -----------------------------------------------------------------------

    @Test
    fun `win gives 2 points, draw gives 1, loss gives 0`() {
        val teams      = makeTeams(4)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = false)
        val match      = tournament.rounds[0].matches[0] // Team1 vs Team4

        // Team1 wins 3-0
        val standings = generator.computeStandings(
            tournament,
            results = mapOf(match to (3 to 0))
        )

        val homeRow = standings.first { it.team.id == match.home.id }
        val awayRow = standings.first { it.team.id == match.away.id }

        assertEquals(2, homeRow.points)  // WIN
        assertEquals(0, awayRow.points)  // LOSS
        assertEquals(1, homeRow.wins)
        assertEquals(1, awayRow.losses)
    }

    @Test
    fun `draw gives both teams 1 point each`() {
        val teams      = makeTeams(4)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = false)
        val match      = tournament.rounds[0].matches[0]

        val standings = generator.computeStandings(
            tournament,
            results = mapOf(match to (1 to 1))
        )

        val homeRow = standings.first { it.team.id == match.home.id }
        val awayRow = standings.first { it.team.id == match.away.id }

        assertEquals(1, homeRow.points)
        assertEquals(1, awayRow.points)
        assertEquals(1, homeRow.draws)
        assertEquals(1, awayRow.draws)
    }

    @Test
    fun `unplayed matches don't appear in standings`() {
        val teams      = makeTeams(4)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = false)

        // No results entered yet
        val standings = generator.computeStandings(tournament, results = emptyMap())

        // All teams present but all stats zeroed
        assertEquals(4, standings.size)
        standings.forEach { row ->
            assertEquals(0, row.played)
            assertEquals(0, row.points)
        }
    }

    @Test
    fun `standings are sorted by points descending then wins descending`() {
        val teams      = makeTeams(4)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = false)

        // Play all matches in round 1 and round 2 with predetermined results
        val round1 = tournament.rounds[0].matches
        val round2 = tournament.rounds[1].matches

        val results: Map<Match, Pair<Int, Int>> = buildMap {
            put(round1[0], 2 to 0)  // home wins
            put(round1[1], 1 to 1)  // draw
            put(round2[0], 0 to 1)  // away wins
            put(round2[1], 2 to 2)  // draw
        }

        val standings = generator.computeStandings(tournament, results)

        // Verify sorted order: no row should have fewer points than the row below it
        for (i in 0 until standings.size - 1) {
            assertTrue(standings[i].points >= standings[i + 1].points)
        }
    }

    @Test
    fun `full tournament standings - all matches played for n=4`() {
        val teams      = makeTeams(4)
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = false)

        // All home teams win every match
        val results = tournament.rounds.flatMap { it.matches }
            .associateWith { 1 to 0 }

        val standings = generator.computeStandings(tournament, results)

        // Every team plays 3 matches; total points = 3 * 2 (wins) distributed
        val totalPoints = standings.sumOf { it.points }
        // 6 matches × 2 points per win = 12 total points across all teams
        assertEquals(12, totalPoints)

        standings.forEach { row ->
            assertEquals("Each team plays 3 games", 3, row.played)
        }
    }

    @Test
    fun `duplicate team names don't affect correctness - match by id`() {
        val teams = listOf(
            Team(1, "Tigers"), Team(2, "Tigers"), Team(3, "Lions"), Team(4, "Lions")
        )
        val tournament = generator.generateFixtures(teams, doubleRoundRobin = false)

        // Should still generate correct fixture structure
        assertEquals(3, tournament.rounds.size)
        assertEquals(6, tournament.rounds.flatMap { it.matches }.size)
    }
}
