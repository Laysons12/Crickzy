package com.example.crickzy.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnockoutSetupScreen(
    viewModel: KnockoutViewModel,
    onNavigateToBracket: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val teamCount = state.teamCount.toIntOrNull()?.coerceIn(2, 64) ?: 0

    LaunchedEffect(state.showBracket) {
        if (state.showBracket) onNavigateToBracket()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Knockout Setup") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                Text("Create Knockout Bracket", style = MaterialTheme.typography.headlineMedium)
            }

            if (viewModel.tournamentId > 0) {
                item {
                    Text(
                        text = "Participating Teams (${teamCount})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        state.teamNames.take(teamCount).forEachIndexed { index, name ->
                            Text(
                                text = "${index + 1}. $name",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = state.teamCount,
                        onValueChange = viewModel::onTeamCountChanged,
                        label = { Text("Number of teams (2 – 64)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.teamCountError != null,
                        supportingText = state.teamCountError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.isRandomSeed,
                        onCheckedChange = viewModel::onRandomSeedToggled
                    )
                    Text("Randomize seeding (shuffle teams)")
                }
                Text(
                    text = "If unchecked, teams are seeded in the order entered (Team 1 is top seed).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 48.dp)
                )
            }

            if (viewModel.tournamentId <= 0 && teamCount >= 2) {
                item {
                    Text("Team names (optional)", style = MaterialTheme.typography.labelLarge)
                }

                itemsIndexed(items = List(teamCount) { it }) { index, _ ->
                    OutlinedTextField(
                        value = state.teamNames.getOrElse(index) { "" },
                        onValueChange = { viewModel.onTeamNameChanged(index, it) },
                        label = { Text("Seed ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Button(
                    onClick = viewModel::onGenerateBracket,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Generate Bracket")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnockoutBracketScreen(
    viewModel: KnockoutViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bracket = state.bracket ?: return

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Knockout Bracket") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.tournamentId > 0) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        TextButton(
                            onClick = {
                                viewModel.saveFixturesToDb(
                                    onSuccess = {
                                        (context as? android.app.Activity)?.setResult(android.app.Activity.RESULT_OK)
                                        (context as? android.app.Activity)?.finish()
                                    },
                                    onFailure = { err ->
                                        android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        ) {
                            Text("Save & Start", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (bracket.champion != null) {
                ChampionBanner(champion = bracket.champion)
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(bracket.rounds) { round ->
                    RoundSection(
                        round = round,
                        onWinnerSelected = viewModel::onMatchWinnerSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun ChampionBanner(champion: Team) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Default.Star, contentDescription = "Trophy", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text("Champion", style = MaterialTheme.typography.labelLarge)
            Text(champion.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoundSection(
    round: KnockoutRound,
    onWinnerSelected: (matchId: Int, winner: Team) -> Unit
) {
    Column {
        Text(
            text = round.roundName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        round.matches.forEach { match ->
            MatchCard(match = match, onWinnerSelected = onWinnerSelected)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MatchCard(
    match: KnockoutMatch,
    onWinnerSelected: (matchId: Int, winner: Team) -> Unit
) {
    var showWinnerDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (match.status == MatchStatus.BYE_ADVANCED) {
                Text("BYE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(4.dp))
                val teamName = match.home?.name ?: match.away?.name ?: "Unknown"
                Text(teamName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        TeamRow(team = match.home, isWinner = match.winner == match.home, placeholder = "Winner of M${match.homeSourceMatchId}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        TeamRow(team = match.away, isWinner = match.winner == match.away, placeholder = "Winner of M${match.awaySourceMatchId}")
                    }

                    if (match.winner == null && match.home != null && match.away != null) {
                        Button(
                            onClick = { showWinnerDialog = true },
                            modifier = Modifier.padding(start = 16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Set Winner")
                        }
                    } else if (match.winner != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Text(
                                "Finished",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWinnerDialog) {
        AlertDialog(
            onDismissRequest = { showWinnerDialog = false },
            title = { Text("Select Winner") },
            text = {
                Column {
                    Text("Who won this match?")
                    Spacer(Modifier.height(16.dp))
                    match.home?.let { team ->
                        Button(
                            onClick = {
                                onWinnerSelected(match.matchId, team)
                                showWinnerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(team.name) }
                        Spacer(Modifier.height(8.dp))
                    }
                    match.away?.let { team ->
                        Button(
                            onClick = {
                                onWinnerSelected(match.matchId, team)
                                showWinnerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(team.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showWinnerDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TeamRow(team: Team?, isWinner: Boolean, placeholder: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = team?.name ?: placeholder,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
            color = if (team == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
        )
        if (isWinner) {
            Icon(
            Icons.Default.Star,
                contentDescription = "Winner",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(16.dp)
            )
        }
    }
}
