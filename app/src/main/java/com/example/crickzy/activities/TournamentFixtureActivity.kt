package com.example.crickzy.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import com.example.crickzy.R
import com.example.crickzy.tournament.*

class TournamentFixtureActivity : ComponentActivity() {

    private val fixtureViewModel: FixtureViewModel by viewModels()
    private val knockoutViewModel: KnockoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tournamentId = intent.getLongExtra("tournamentId", -1L)
        val tournamentFormat = intent.getStringExtra("tournamentFormat") ?: "League"
        val teamCount = intent.getIntExtra("teamCount", 4)
        val teamNames = intent.getStringArrayListExtra("teamNames") ?: emptyList()

        if (tournamentFormat == "League") {
            fixtureViewModel.setTournamentId(tournamentId)
            fixtureViewModel.onTeamCountChanged(teamCount.toString())
            teamNames.forEachIndexed { index, name ->
                fixtureViewModel.onTeamNameChanged(index, name)
            }
        } else {
            knockoutViewModel.setTournamentId(tournamentId)
            knockoutViewModel.onTeamCountChanged(teamCount.toString())
            teamNames.forEachIndexed { index, name ->
                knockoutViewModel.onTeamNameChanged(index, name)
            }
        }

        setContent {
            val appBackground = colorResource(id = R.color.background)
            val appPrimary = colorResource(id = R.color.primary)
            val appSurface = colorResource(id = R.color.surface)
            val appOnPrimary = colorResource(id = R.color.white)
            val appTextPrimary = colorResource(id = R.color.text_primary)

            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val customColorScheme = if (isDark) {
                darkColorScheme(
                    primary = appPrimary,
                    background = appBackground,
                    surface = appSurface,
                    onPrimary = appOnPrimary,
                    onBackground = appTextPrimary,
                    onSurface = appTextPrimary,
                    secondary = colorResource(id = R.color.secondary),
                    surfaceVariant = colorResource(id = R.color.tab_unselected_bg)
                )
            } else {
                lightColorScheme(
                    primary = appPrimary,
                    background = appBackground,
                    surface = appSurface,
                    onPrimary = appOnPrimary,
                    onBackground = appTextPrimary,
                    onSurface = appTextPrimary,
                    secondary = colorResource(id = R.color.secondary),
                    surfaceVariant = colorResource(id = R.color.tab_unselected_bg)
                )
            }

            MaterialTheme(
                colorScheme = customColorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (tournamentFormat == "League") {
                        LeagueFlow(
                            viewModel = fixtureViewModel,
                            onFinish = { finish() }
                        )
                    } else {
                        KnockoutFlow(
                            viewModel = knockoutViewModel,
                            onFinish = { finish() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LeagueFlow(viewModel: FixtureViewModel, onFinish: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    if (state.showResults) {
        androidx.activity.compose.BackHandler {
            viewModel.onBackToSetup()
        }
        FixtureResultScreen(
            viewModel = viewModel,
            onBack = viewModel::onBackToSetup
        )
    } else {
        FixtureSetupScreen(
            viewModel = viewModel,
            onNavigateToResults = { },
            onBack = onFinish
        )
    }
}

@Composable
fun KnockoutFlow(viewModel: KnockoutViewModel, onFinish: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    if (state.showBracket) {
        androidx.activity.compose.BackHandler {
            viewModel.onBackToSetup()
        }
        KnockoutBracketScreen(
            viewModel = viewModel,
            onBack = viewModel::onBackToSetup
        )
    } else {
        KnockoutSetupScreen(
            viewModel = viewModel,
            onNavigateToBracket = { },
            onBack = onFinish
        )
    }
}
