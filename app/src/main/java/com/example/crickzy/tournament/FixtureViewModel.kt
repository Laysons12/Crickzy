package com.example.crickzy.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

data class FixtureUiState(
    // Setup inputs
    val teamCount: String = "4",
    val teamNames: List<String> = emptyList(),   // custom names; empty = use defaults
    val isDoubleRoundRobin: Boolean = false,

    // Validation
    val teamCountError: String? = null,

    // Generated data
    val tournament: Tournament? = null,

    // Points table: match → (homeScore, awayScore); null score = not played yet
    val matchResults: Map<Match, Pair<Int, Int>> = emptyMap(),

    // Derived / cached standings (recomputed whenever matchResults changes)
    val standings: List<StandingRow> = emptyList(),

    // Navigation
    val showResults: Boolean = false
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class FixtureViewModel(
    private val fixtureGenerator: FixtureGenerator = FixtureGenerator()
) : ViewModel() {

    var tournamentId: Long = -1L
        private set

    fun setTournamentId(id: Long) {
        tournamentId = id
    }

    private val _uiState = MutableStateFlow(FixtureUiState())
    val uiState: StateFlow<FixtureUiState> = _uiState.asStateFlow()

    fun saveFixturesToDb(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val state = _uiState.value
        val tournamentData = state.tournament ?: return onFailure("No fixtures generated yet.")
        val tid = tournamentId
        if (tid <= 0) return onFailure("Invalid tournament ID.")

        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.crickzy.database.SupabaseHelper.resetTournamentFixtures(tid)

                    var matchNum = 1
                    val leagueFixtures = mutableListOf<com.example.crickzy.models.Fixture>()

                    for (round in tournamentData.rounds) {
                        val roundLabel = if (tournamentData.doubleRoundRobin) "League Rd ${round.roundNumber}" else "League"
                        for (match in round.matches) {
                            leagueFixtures.add(com.example.crickzy.models.Fixture(
                                tournamentId = tid,
                                team1Name = match.home.name,
                                team2Name = match.away.name,
                                matchNumber = matchNum,
                                round = roundLabel,
                                status = "Upcoming"
                            ))
                            matchNum++
                        }
                    }

                    val teamCount = tournamentData.teams.size
                    if (teamCount >= 4) {
                        leagueFixtures.add(com.example.crickzy.models.Fixture(tournamentId = tid, team1Name = "TBD", team2Name = "TBD", matchNumber = matchNum, round = "Semi-Final"))
                        matchNum++
                        leagueFixtures.add(com.example.crickzy.models.Fixture(tournamentId = tid, team1Name = "TBD", team2Name = "TBD", matchNumber = matchNum, round = "Semi-Final"))
                        matchNum++
                        leagueFixtures.add(com.example.crickzy.models.Fixture(tournamentId = tid, team1Name = "TBD", team2Name = "TBD", matchNumber = matchNum, round = "Final"))
                    } else if (teamCount >= 2) {
                        leagueFixtures.add(com.example.crickzy.models.Fixture(tournamentId = tid, team1Name = "TBD", team2Name = "TBD", matchNumber = matchNum, round = "Final"))
                    }

                    for (f in leagueFixtures) {
                        com.example.crickzy.database.SupabaseHelper.addFixture(f)
                    }

                    val tour = com.example.crickzy.database.SupabaseHelper.getTournamentById(tid)
                    tour?.let {
                        it.format = "League"
                        it.fixturesCreated = true
                        com.example.crickzy.database.SupabaseHelper.updateTournament(it)
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to save fixtures.")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Setup screen interactions
    // -----------------------------------------------------------------------

    fun onTeamCountChanged(value: String) {
        _uiState.update { it.copy(teamCount = value, teamCountError = null) }
    }

    fun onTeamNameChanged(index: Int, name: String) {
        _uiState.update { state ->
            val updated = state.teamNames.toMutableList().also { list ->
                while (list.size <= index) list.add("")
                list[index] = name
            }
            state.copy(teamNames = updated)
        }
    }

    fun onFormatToggled(isDouble: Boolean) {
        _uiState.update { it.copy(isDoubleRoundRobin = isDouble) }
    }

    fun onGenerateFixtures() {
        val state = _uiState.value

        // Validate team count
        val n = state.teamCount.toIntOrNull()
        when {
            n == null || n < 2 -> {
                _uiState.update { it.copy(teamCountError = "Enter a number between 2 and 20.") }
                return
            }
            n > 20 -> {
                _uiState.update { it.copy(teamCountError = "Maximum 20 teams supported.") }
                return
            }
        }

        requireNotNull(n)

        val teams = buildTeams(n, state.teamNames)

        viewModelScope.launch {
            val tournament = fixtureGenerator.generateFixtures(teams, state.isDoubleRoundRobin)
            _uiState.update { it ->
                it.copy(
                    tournament    = tournament,
                    matchResults  = emptyMap(),
                    standings     = fixtureGenerator.computeStandings(tournament, emptyMap()),
                    showResults   = true,
                    teamCountError = null
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Results screen interactions
    // -----------------------------------------------------------------------

    /**
     * Record the score for a match and immediately recompute standings.
     *
     * Call this whenever the user confirms a score for a fixture.
     */
    fun onMatchResultEntered(match: Match, homeScore: Int, awayScore: Int) {
        _uiState.update { state ->
            val updatedResults = state.matchResults + (match to (homeScore to awayScore))
            val tournament     = state.tournament ?: return@update state

            state.copy(
                matchResults = updatedResults,
                standings    = fixtureGenerator.computeStandings(tournament, updatedResults)
            )
        }
    }

    /**
     * Remove a previously entered score (e.g. to correct a mistake).
     */
    fun onMatchResultCleared(match: Match) {
        _uiState.update { state ->
            val updatedResults = state.matchResults - match
            val tournament     = state.tournament ?: return@update state

            state.copy(
                matchResults = updatedResults,
                standings    = fixtureGenerator.computeStandings(tournament, updatedResults)
            )
        }
    }

    fun onBackToSetup() {
        _uiState.update { it.copy(showResults = false) }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun buildTeams(n: Int, customNames: List<String>): List<Team> =
        (1..n).map { i ->
            val name = customNames.getOrNull(i - 1)?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Team $i"
            Team(id = i, name = name)
        }
}
