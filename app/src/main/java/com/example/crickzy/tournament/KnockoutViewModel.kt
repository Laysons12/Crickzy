package com.example.crickzy.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KnockoutUiState(
    val teamCount: String = "8",
    val teamNames: List<String> = emptyList(),
    val isRandomSeed: Boolean = false,
    val teamCountError: String? = null,
    val bracket: KnockoutBracket? = null,
    val showBracket: Boolean = false
)

class KnockoutViewModel(
    private val generator: KnockoutFixtureGenerator = KnockoutFixtureGenerator()
) : ViewModel() {

    var tournamentId: Long = -1L
        private set

    fun setTournamentId(id: Long) {
        tournamentId = id
    }

    private val _uiState = MutableStateFlow(KnockoutUiState())
    val uiState: StateFlow<KnockoutUiState> = _uiState.asStateFlow()

    fun saveFixturesToDb(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val state = _uiState.value
        val bracket = state.bracket ?: return onFailure("No bracket generated yet.")
        val tid = tournamentId
        if (tid <= 0) return onFailure("Invalid tournament ID.")

        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.crickzy.database.SupabaseHelper.resetTournamentFixtures(tid)

                    var matchNum = 1
                    val knockoutFixtures = mutableListOf<com.example.crickzy.models.Fixture>()

                    for (round in bracket.rounds) {
                        val roundLabel = when (round.roundName) {
                            "Semi-finals" -> "Semi-Final"
                            "Quarter-finals" -> "Quarter-Final"
                            else -> round.roundName
                        }
                        for (match in round.matches) {
                            if (match.status == MatchStatus.BYE_ADVANCED) continue

                            knockoutFixtures.add(com.example.crickzy.models.Fixture(
                                tournamentId = tid,
                                team1Name = match.home?.name ?: "TBD",
                                team2Name = match.away?.name ?: "TBD",
                                matchNumber = matchNum,
                                round = roundLabel,
                                status = "Upcoming"
                            ))
                            matchNum++
                        }
                    }

                    for (f in knockoutFixtures) {
                        com.example.crickzy.database.SupabaseHelper.addFixture(f)
                    }

                    val tour = com.example.crickzy.database.SupabaseHelper.getTournamentById(tid)
                    tour?.let {
                        it.format = "Knockout"
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

    fun onRandomSeedToggled(isRandom: Boolean) {
        _uiState.update { it.copy(isRandomSeed = isRandom) }
    }

    fun onGenerateBracket() {
        val state = _uiState.value
        val n = state.teamCount.toIntOrNull()
        if (n == null || n !in 2..64) {
            _uiState.update { it.copy(teamCountError = "Enter a number between 2 and 64.") }
            return
        }

        val teams = (1..n).map { i ->
            val name = state.teamNames.getOrNull(i - 1)?.trim()?.takeIf { it.isNotEmpty() } ?: "Team $i"
            Team(id = i, name = name)
        }

        viewModelScope.launch {
            val bracket = generator.generateKnockoutBracket(teams, state.isRandomSeed)
            _uiState.update {
                it.copy(
                    bracket = bracket,
                    showBracket = true,
                    teamCountError = null
                )
            }
        }
    }

    fun onMatchWinnerSelected(matchId: Int, winner: Team) {
        _uiState.update { state ->
            val bracket = state.bracket ?: return@update state
            val newBracket = advanceWinner(bracket, matchId, winner)
            state.copy(bracket = newBracket)
        }
    }

    fun onBackToSetup() {
        _uiState.update { it.copy(showBracket = false) }
    }

    private fun advanceWinner(bracket: KnockoutBracket, matchId: Int, winner: Team): KnockoutBracket {
        // Deep copy rounds and update the match winner
        val newRounds = bracket.rounds.map { round ->
            round.copy(matches = round.matches.map { match ->
                if (match.matchId == matchId) {
                    match.copy(winner = winner, status = MatchStatus.COMPLETED)
                } else match
            })
        }

        // Propagate the winner to downstream matches
        val propagatedRounds = newRounds.map { round ->
            round.copy(matches = round.matches.map { match ->
                var updatedMatch = match
                // If this match depends on the changed match as home source
                if (match.homeSourceMatchId == matchId) {
                    updatedMatch = updatedMatch.copy(home = winner)
                }
                // If this match depends on the changed match as away source
                if (match.awaySourceMatchId == matchId) {
                    updatedMatch = updatedMatch.copy(away = winner)
                }
                updatedMatch
            })
        }

        // Check if champion is decided
        val finalRound = propagatedRounds.lastOrNull()
        val champion = finalRound?.matches?.firstOrNull()?.winner

        return bracket.copy(rounds = propagatedRounds, champion = champion)
    }
}
