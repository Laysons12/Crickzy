package com.example.crickzy.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun FixtureSetupScreen(
    viewModel: FixtureViewModel,
    onNavigateToResults: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Derived: how many team-name fields to show
    val teamCount = state.teamCount.toIntOrNull()?.coerceIn(2, 20) ?: 0

    // Navigate when tournament is generated
    LaunchedEffect(state.showResults) {
        if (state.showResults) onNavigateToResults()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("League Setup") },
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
                Text("Create League Tournament", style = MaterialTheme.typography.headlineMedium)
            }

            // ── Team count ──────────────────────────────────────────────────────
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
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
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
                        value           = state.teamCount,
                        onValueChange   = viewModel::onTeamCountChanged,
                        label           = { Text("Number of teams (2 – 20)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError         = state.teamCountError != null,
                        supportingText  = state.teamCountError?.let { { Text(it) } },
                        singleLine      = true,
                        modifier        = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Format toggle ────────────────────────────────────────────────────
            item {
                Column {
                    Text("Format", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !state.isDoubleRoundRobin,
                            onClick  = { viewModel.onFormatToggled(false) }
                        )
                        Text("Single round-robin", Modifier.padding(start = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.isDoubleRoundRobin,
                            onClick  = { viewModel.onFormatToggled(true) }
                        )
                        Text("Double round-robin (home & away)", Modifier.padding(start = 4.dp))
                    }
                }
            }

            // ── Optional team names ──────────────────────────────────────────────
            if (viewModel.tournamentId <= 0 && teamCount >= 2) {
                item {
                    Text(
                        "Team names (optional — leave blank for defaults)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                itemsIndexed(items = List(teamCount) { it }) { index, _ ->
                    OutlinedTextField(
                        value         = state.teamNames.getOrElse(index) { "" },
                        onValueChange = { viewModel.onTeamNameChanged(index, it) },
                        label         = { Text("Team ${index + 1}") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Generate button ──────────────────────────────────────────────────
            item {
                Button(
                    onClick  = viewModel::onGenerateFixtures,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Generate Fixtures")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Result screen — two tabs: Fixtures | Points Table
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixtureResultScreen(
    viewModel: FixtureViewModel,
    onBack: () -> Unit
) {
    val state      by viewModel.uiState.collectAsStateWithLifecycle()
    val tournament  = state.tournament ?: return

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Fixtures", "Points Table")

    // Summary values
    val totalRounds  = tournament.rounds.size
    val matchesPerRound = tournament.rounds.maxOfOrNull { it.matches.size } ?: 0
    val totalMatches = tournament.rounds.sumOf { it.matches.size }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("League Fixtures") },
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
            // ── Summary banner ───────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.padding(12.dp)
                ) {
                    SummaryChip("Rounds", totalRounds.toString())
                    SummaryChip("Per round", matchesPerRound.toString())
                    SummaryChip("Total", totalMatches.toString())
                    if (tournament.doubleRoundRobin) {
                        SummaryChip("Format", "H&A")
                    }
                }
            }

            // ── Tab row ──────────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(title) }
                    )
                }
            }

            // ── Tab content ──────────────────────────────────────────────────
            when (selectedTab) {
                0 -> FixturesTab(
                    tournament   = tournament,
                    matchResults = state.matchResults,
                    onResultEntered = { match, home, away ->
                        viewModel.onMatchResultEntered(match, home, away)
                    },
                    onResultCleared = viewModel::onMatchResultCleared
                )
                1 -> PointsTableTab(standings = state.standings)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab 1 — Fixtures
// ---------------------------------------------------------------------------

@Composable
private fun FixturesTab(
    tournament: Tournament,
    matchResults: Map<Match, Pair<Int, Int>>,
    onResultEntered: (Match, Int, Int) -> Unit,
    onResultCleared: (Match) -> Unit
) {
    val singleRoundCount = if (tournament.doubleRoundRobin)
        tournament.rounds.size / 2 else tournament.rounds.size

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tournament.rounds) { round ->
            val isReturnLeg = tournament.doubleRoundRobin && round.roundNumber > singleRoundCount
            val roundLabel  = if (isReturnLeg)
                "Round ${round.roundNumber}  ·  Return leg"
            else
                "Round ${round.roundNumber}"

            RoundCard(
                roundLabel   = roundLabel,
                round        = round,
                matchResults = matchResults,
                onResultEntered = onResultEntered,
                onResultCleared = onResultCleared
            )
        }
    }
}

@Composable
private fun RoundCard(
    roundLabel: String,
    round: Round,
    matchResults: Map<Match, Pair<Int, Int>>,
    onResultEntered: (Match, Int, Int) -> Unit,
    onResultCleared: (Match) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(roundLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            round.matches.forEach { match ->
                MatchRow(
                    match           = match,
                    result          = matchResults[match],
                    onResultEntered = onResultEntered,
                    onResultCleared = onResultCleared
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            round.byeTeam?.let { bye ->
                Text(
                    text  = "${bye.name} — bye",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MatchRow(
    match: Match,
    result: Pair<Int, Int>?,
    onResultEntered: (Match, Int, Int) -> Unit,
    onResultCleared: (Match) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Team names + score
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(match.home.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))

            if (result != null) {
                Text(
                    text      = "${result.first}  –  ${result.second}",
                    style     = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier  = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                Text(
                    text     = "vs",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Text(
                match.away.name,
                style    = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier  = Modifier.weight(1f)
            )
        }

        // Edit button
        IconButton(onClick = { showDialog = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Enter score",
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showDialog) {
        ScoreEntryDialog(
            match    = match,
            existing = result,
            onConfirm = { h, a ->
                onResultEntered(match, h, a)
                showDialog = false
            },
            onClear = {
                onResultCleared(match)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun ScoreEntryDialog(
    match: Match,
    existing: Pair<Int, Int>?,
    onConfirm: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var homeScore by remember { mutableStateOf(existing?.first?.toString() ?: "") }
    var awayScore by remember { mutableStateOf(existing?.second?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${match.home.name}  vs  ${match.away.name}")
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value           = homeScore,
                        onValueChange   = { homeScore = it },
                        label           = { Text(match.home.name) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine      = true,
                        modifier        = Modifier.weight(1f)
                    )
                    Text("–")
                    OutlinedTextField(
                        value           = awayScore,
                        onValueChange   = { awayScore = it },
                        label           = { Text(match.away.name) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine      = true,
                        modifier        = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h = homeScore.toIntOrNull() ?: return@TextButton
                    val a = awayScore.toIntOrNull() ?: return@TextButton
                    onConfirm(h, a)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Tab 2 — Points Table
// ---------------------------------------------------------------------------

@Composable
private fun PointsTableTab(standings: List<StandingRow>) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        PointsTableHeader()
        HorizontalDivider(thickness = 2.dp)

        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(standings) { row ->
                PointsTableRow(row)
                HorizontalDivider()
            }
        }

        // Legend
        Text(
            text     = "Points: Win = ${PointsSystem.WIN_POINTS}  ·  Draw = ${PointsSystem.DRAW_POINTS}  ·  Loss = ${PointsSystem.LOSS_POINTS}",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun PointsTableHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("#",   style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
        Text("Team",style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        HeaderCell("P")
        HeaderCell("W")
        HeaderCell("D")
        HeaderCell("L")
        HeaderCell("Pts", bold = true)
    }
}

@Composable
private fun PointsTableRow(row: StandingRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            row.position.toString(),
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(28.dp),
            color    = MaterialTheme.colorScheme.outline
        )
        Text(
            row.team.name,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        DataCell(row.played.toString())
        DataCell(row.wins.toString())
        DataCell(row.draws.toString())
        DataCell(row.losses.toString())
        DataCell(row.points.toString(), bold = true)
    }
}

@Composable
private fun HeaderCell(text: String, bold: Boolean = false) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign  = TextAlign.Center,
        modifier   = Modifier.width(36.dp)
    )
}

@Composable
private fun DataCell(text: String, bold: Boolean = false) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign  = TextAlign.Center,
        modifier   = Modifier.width(36.dp)
    )
}

// ---------------------------------------------------------------------------
// Shared composables
// ---------------------------------------------------------------------------

@Composable
private fun SummaryChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
