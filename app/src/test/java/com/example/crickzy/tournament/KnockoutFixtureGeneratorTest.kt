package com.example.crickzy.tournament

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KnockoutFixtureGeneratorTest {

    private lateinit var generator: KnockoutFixtureGenerator

    @Before
    fun setUp() {
        generator = KnockoutFixtureGenerator()
    }

    private fun makeTeams(n: Int) = (1..n).map { Team(id = it, name = "Team $it") }

    @Test
    fun `n=4 knockout - generates 2 rounds with correct matches`() {
        val teams = makeTeams(4)
        val bracket = generator.generateKnockoutBracket(teams, randomSeed = false)

        assertEquals(2, bracket.rounds.size)
        assertEquals("Semi-finals", bracket.rounds[0].roundName)
        assertEquals(2, bracket.rounds[0].matches.size)

        assertEquals("Final", bracket.rounds[1].roundName)
        assertEquals(1, bracket.rounds[1].matches.size)

        val r1m1 = bracket.rounds[0].matches[0]
        assertEquals("Team 1", r1m1.home?.name)
        assertEquals("Team 4", r1m1.away?.name)

        val r1m2 = bracket.rounds[0].matches[1]
        assertEquals("Team 2", r1m2.home?.name)
        assertEquals("Team 3", r1m2.away?.name)
    }

    @Test
    fun `n=5 knockout - creates 3 rounds, pads to 8 with 3 byes`() {
        val teams = makeTeams(5)
        val bracket = generator.generateKnockoutBracket(teams, randomSeed = false)

        assertEquals(3, bracket.rounds.size)
        assertEquals("Quarter-finals", bracket.rounds[0].roundName)
        assertEquals(4, bracket.rounds[0].matches.size)

        val r1 = bracket.rounds[0]
        val byes = r1.matches.filter { it.status == MatchStatus.BYE_ADVANCED }
        assertEquals("3 byes needed to pad 5 to 8", 3, byes.size)

        val topSeedsWithByes = byes.mapNotNull { it.winner?.name }
        assertTrue(topSeedsWithByes.contains("Team 1"))
        assertTrue(topSeedsWithByes.contains("Team 2"))
        assertTrue(topSeedsWithByes.contains("Team 3"))
    }

    @Test
    fun `n=8 knockout - correct seeding pattern`() {
        val teams = makeTeams(8)
        val bracket = generator.generateKnockoutBracket(teams, randomSeed = false)

        assertEquals(3, bracket.rounds.size)
        val r1 = bracket.rounds[0]

        assertEquals("Team 1", r1.matches[0].home?.name)
        assertEquals("Team 8", r1.matches[0].away?.name)

        assertEquals("Team 4", r1.matches[1].home?.name)
        assertEquals("Team 5", r1.matches[1].away?.name)

        assertEquals("Team 2", r1.matches[2].home?.name)
        assertEquals("Team 7", r1.matches[2].away?.name)

        assertEquals("Team 3", r1.matches[3].home?.name)
        assertEquals("Team 6", r1.matches[3].away?.name)
    }

    @Test
    fun `n=6 knockout - 2 byes for top 2 seeds`() {
        val teams = makeTeams(6)
        val bracket = generator.generateKnockoutBracket(teams, randomSeed = false)
        
        assertEquals(3, bracket.rounds.size) // 8 slots -> 3 rounds
        val r1 = bracket.rounds[0]
        
        // M1: 1 vs null (BYE)
        assertEquals(MatchStatus.BYE_ADVANCED, r1.matches[0].status)
        assertEquals("Team 1", r1.matches[0].winner?.name)

        // M3: 2 vs null (BYE)
        assertEquals(MatchStatus.BYE_ADVANCED, r1.matches[2].status)
        assertEquals("Team 2", r1.matches[2].winner?.name)

        // M2 and M4 should be normal matches
        assertEquals(MatchStatus.SCHEDULED, r1.matches[1].status)
        assertEquals(MatchStatus.SCHEDULED, r1.matches[3].status)
    }

    @Test
    fun `random seeding shuffles teams`() {
        val teams = makeTeams(8)
        // With random seed, it's highly unlikely that 1 plays 8, 4 plays 5, etc in exact order
        var differentOrderFound = false
        for (i in 0 until 10) {
            val bracket = generator.generateKnockoutBracket(teams, randomSeed = true)
            val r1 = bracket.rounds[0]
            val firstMatchHome = r1.matches[0].home?.name
            val firstMatchAway = r1.matches[0].away?.name
            if (firstMatchHome != "Team 1" || firstMatchAway != "Team 8") {
                differentOrderFound = true
                break
            }
        }
        assertTrue("Random seeding should shuffle teams", differentOrderFound)
    }
}
