package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.GameSave
import kotlin.math.roundToInt

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val gameSave by viewModel.gameSave.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val activeThief by viewModel.activeThief.collectAsState()
    val activeComplaint by viewModel.activeComplaint.collectAsState()
    val journalLogs by viewModel.journalLogs.collectAsState()

    var activeTab by remember { mutableStateOf("COUNTER") } // COUNTER, WHOLESALE, UPGRADES, JOURNAL

    // Dialog for Game Winner at Level 100
    var showWinnerDialog by remember { mutableStateOf(false) }
    LaunchedEffect(gameSave.completedMainQuest) {
        if (gameSave.completedMainQuest) {
            showWinnerDialog = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                NavigationBarItem(
                    selected = activeTab == "COUNTER",
                    onClick = { activeTab = "COUNTER" },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Front Counter") },
                    label = { Text("Counter") },
                    modifier = Modifier.testTag("tab_counter")
                )
                NavigationBarItem(
                    selected = activeTab == "WHOLESALE",
                    onClick = { activeTab = "WHOLESALE" },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Wholesale Supply") },
                    label = { Text("Buy Stock") },
                    modifier = Modifier.testTag("tab_wholesale")
                )
                NavigationBarItem(
                    selected = activeTab == "UPGRADES",
                    onClick = { activeTab = "UPGRADES" },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Upgrades") },
                    label = { Text("Upgrades") },
                    modifier = Modifier.testTag("tab_upgrades")
                )
                NavigationBarItem(
                    selected = activeTab == "JOURNAL",
                    onClick = { activeTab = "JOURNAL" },
                    icon = { Icon(Icons.Default.List, contentDescription = "Business Logs") },
                    label = { Text("Logs") },
                    modifier = Modifier.testTag("tab_journal")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top Dashboard Banner: Level, Cash, Popularity
            MainStatsHeader(gameSave = gameSave, viewModel = viewModel)

            // Dynamic Procedural Visual of Shop/Stall
            ShopVisualBanner(
                stage = gameSave.shopStage, 
                level = gameSave.level,
                title = viewModel.getStageTitle(gameSave.shopStage)
            )

            // Flashing thief alarm system
            AnimatedVisibility(
                visible = activeThief != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                activeThief?.let { thief ->
                    ThiefAlertCard(thief = thief, onCatch = { viewModel.catchThiefManual() })
                }
            }

            // Tab Page Contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    "COUNTER" -> CounterTab(
                        gameSave = gameSave,
                        customers = customers,
                        logs = journalLogs,
                        viewModel = viewModel
                    )
                    "WHOLESALE" -> WholesaleTab(
                        gameSave = gameSave,
                        viewModel = viewModel
                    )
                    "UPGRADES" -> UpgradesTab(
                        gameSave = gameSave,
                        viewModel = viewModel
                    )
                    "JOURNAL" -> JournalTab(
                        logs = journalLogs,
                        onReset = { viewModel.triggerHardReset() }
                    )
                }
            }
        }
    }

    // Active Complaint handling overlay dialog
    if (activeComplaint != null) {
        ComplaintDialog(
            customer = activeComplaint!!,
            hasCashForRefund = gameSave.cash >= 5,
            hasCashForTea = gameSave.cash >= 2,
            onResolve = { refund, freeTea -> viewModel.resolveComplaint(refund, freeTea) }
        )
    }

    // Level 100 Trophy Dialog
    if (showWinnerDialog) {
        WinnerTrophyDialog(
            level = gameSave.level,
            totalEarnings = gameSave.totalEarnings,
            onDismiss = { showWinnerDialog = false }
        )
    }
}

@Composable
fun MainStatsHeader(gameSave: GameSave, viewModel: GameViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Level Circle progress indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "LVL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            Text(
                                text = gameSave.level.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        val currentXp = gameSave.experience
                        val nextXp = viewModel.getXpRequired(gameSave.level)
                        val progress = if (nextXp > 0) currentXp.toFloat() / nextXp.toFloat() else 0f

                        Text(
                            text = if (gameSave.level >= 100) "Master Merchant 🏆" else "Progress to Lvl ${gameSave.level + 1}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        LinearProgressIndicator(
                            progress = progress,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier
                                .width(120.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }

                // Game Capital (Cash Rupees) Cards
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "₹",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Black
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = gameSave.cash.toString(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("game_cash_text")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub Status Details: Popularity stars, storage capacities
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Popularity
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Popularity Status",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Popularity: ${gameSave.popularity}%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // Storage occupancy
                val currentStock = viewModel.getTotalInventoryCount(gameSave)
                val isMaxSpace = currentStock >= gameSave.maxStock
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Storage capacity",
                        tint = if (isMaxSpace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Storage: $currentStock/${gameSave.maxStock}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMaxSpace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ShopVisualBanner(stage: String, level: Int, title: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (stage == "MALL" || stage == "SHOWROOM") {
                            listOf(Color(0xFF0F2027), Color(0xFF203A43))
                        } else {
                            listOf(Color(0xFFE8ECEF), Color(0xFFCFD8DC))
                        }
                    )
                )
        ) {
            // Draw procedural illustrations
            ShopArtCanvas(stage = stage, modifier = Modifier.fillMaxSize())

            // Overlay status tag
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "$title (Level $level)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ShopArtCanvas(stage: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        when (stage) {
            "EMPTY_STALL" -> {
                // Procedural Wooden Stall
                val stallColor = Color(0xFF8D6E63)
                // Legs
                drawRect(
                    color = stallColor,
                    topLeft = Offset(width * 0.25f, height * 0.4f),
                    size = Size(15f, height * 0.6f)
                )
                drawRect(
                    color = stallColor,
                    topLeft = Offset(width * 0.7f, height * 0.4f),
                    size = Size(15f, height * 0.6f)
                )
                // Counter Board
                drawRect(
                    color = Color(0xFF795548),
                    topLeft = Offset(width * 0.15f, height * 0.35f),
                    size = Size(width * 0.7f, 30f)
                )
                // Old Cardboard sign
                drawRect(
                    color = Color(0xFFFFCC80),
                    topLeft = Offset(width * 0.4f, height * 0.15f),
                    size = Size(width * 0.2f, 25f),
                    style = Stroke(width = 3f)
                )
            }
            "BAZAAR" -> {
                // Saffron striped bazaar canopy tent
                val path = Path().apply {
                    moveTo(width * 0.1f, height * 0.8f)
                    lineTo(width * 0.3f, height * 0.15f)
                    lineTo(width * 0.7f, height * 0.15f)
                    lineTo(width * 0.9f, height * 0.8f)
                    close()
                }
                drawPath(path, color = Color(0xFFFF9800))
                
                // Stripes
                val stripeWidth = width * 0.1f
                for (i in 3..6) {
                    val x = width * i * 0.1f
                    drawLine(
                        color = Color(0xFFFF5722),
                        start = Offset(x, height * 0.15f),
                        end = Offset(x + (if (i < 5) -30f else 30f), height * 0.8f),
                        strokeWidth = 15f
                    )
                }
            }
            "BRICK" -> {
                // Red Brick wall & tiled roof
                drawRect(
                    color = Color(0xFFD32F2F),
                    topLeft = Offset(width * 0.15f, height * 0.25f),
                    size = Size(width * 0.7f, height * 0.75f)
                )
                // Mortar joints lines
                for (y in 4..10) {
                    drawLine(
                        color = Color(0xFFFFCDD2),
                        start = Offset(width * 0.15f, height * y * 0.1f),
                        end = Offset(width * 0.85f, height * y * 0.1f),
                        strokeWidth = 2f
                    )
                }
                // Glass window
                drawRect(
                    color = Color(0xFFB3E5FC),
                    topLeft = Offset(width * 0.35f, height * 0.4f),
                    size = Size(80f, 40f)
                )
            }
            "SHOWROOM" -> {
                // Blue glass and glowing neon outlines
                drawRect(
                    color = Color(0xFF1A237E),
                    topLeft = Offset(0f, 0f),
                    size = Size(width, height)
                )
                // Neon frame
                drawRect(
                    color = Color(0xFF00E5FF),
                    topLeft = Offset(20f, 10f),
                    size = Size(width - 40f, height - 20f),
                    style = Stroke(width = 4f)
                )
                // Abstract storefront clothing hangar
                drawLine(
                    color = Color(0xFFFFEE58),
                    start = Offset(width * 0.4f, height * 0.5f),
                    end = Offset(width * 0.6f, height * 0.5f),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
            "MALL" -> {
                // Modern skyscrapers architecture
                val metalGray = Color(0xFFECEFF1)
                // Left tower
                drawRect(
                    color = Color(0xFF455A64),
                    topLeft = Offset(width * 0.1f, height * 0.1f),
                    size = Size(width * 0.35f, height * 0.9f)
                )
                // Right tower
                drawRect(
                    color = Color(0xFF37474F),
                    topLeft = Offset(width * 0.5f, height * 0.2f),
                    size = Size(width * 0.4f, height * 0.8f)
                )
                // Glowing windows
                for (x in listOf(0.18f, 0.28f, 0.58f, 0.72f)) {
                    for (y in listOf(0.3f, 0.5f, 0.7f)) {
                        drawRect(
                            color = Color(0xFFFFF176),
                            topLeft = Offset(width * x, height * y),
                            size = Size(18f, 10f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThiefAlertCard(thief: ThiefAlert, onCatch: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Red, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🕵️", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "🚨 THIEF SPOTTED!",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                    Text(
                        text = "${thief.thiefName} tries to steal ${thief.quantity}x ${thief.targetItem.displayName}!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onCatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .testTag("catch_thief_button")
                    .padding(start = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CATCH!", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text("${thief.secondsRemaining}s", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CounterTab(
    gameSave: GameSave,
    customers: List<GameCustomer>,
    logs: List<String>,
    viewModel: GameViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        // Active Queue Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Customers Queue (${customers.size}/${viewModel.getQueueLimit(gameSave.shopStage)})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            // Dynamic advertise boost button
            Button(
                onClick = { viewModel.runAdvertisingBoost() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, contentDescription = "Ads", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Advertise", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Horizontal Queue List
        if (customers.isEmpty()) {
            EmptyQueueCard(
                hasCash = gameSave.cash > 0,
                onSweep = { viewModel.cleanStallOddJob() },
                onAd = { viewModel.runAdvertisingBoost() }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxWidth()
            ) {
                items(customers, key = { it.id }) { customer ->
                    val stockOwned = viewModel.getStockCount(gameSave, customer.wantedItem)
                    CustomerRow(
                        customer = customer,
                        stockOwned = stockOwned,
                        onServe = { viewModel.serveCustomer(customer) },
                        onRefuse = { viewModel.refuseCustomer(customer) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Running Live ticker terminal
        Text(
            text = "Business Journal",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(0.45f)
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { log ->
                    Row {
                        Text(
                            text = ">",
                            color = Color(0xFF00E676),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = log,
                            color = Color(0xFFECEFF1),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyQueueCard(hasCash: Boolean, onSweep: () -> Unit, onAd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "No customers yet",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Storefront is Empty",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Wait for someone to walk up, run an online Ad Campaign, or sweep floorboards for missing capital coins!",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Sweep Odd job button
                Button(
                    onClick = onSweep,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("sweep_stall_button")
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Sweep Stall", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clean Stall (+₹)", fontSize = 12.sp)
                }

                if (hasCash) {
                    FilledTonalButton(
                        onClick = onAd,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Shout Promo", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerRow(
    customer: GameCustomer,
    stockOwned: Int,
    onServe: () -> Unit,
    onRefuse: () -> Unit
) {
    val canServe = stockOwned >= customer.quantity
    
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (customer.isAngry) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Person Character Emoji with glowing circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(45.dp)
                    .background(
                        color = if (customer.isAngry) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
            ) {
                Text(customer.avatarEmoji, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Center details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (customer.isAngry) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "😠 ANGRY",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Customer demands
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Wants: ",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${customer.quantity}x ${customer.wantedItem.displayName}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Patience sliding timeline meter
                val progressColor = when {
                    customer.patience > 0.60f -> Color(0xFF4CAF50)
                    customer.patience > 0.30f -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }
                LinearProgressIndicator(
                    progress = customer.patience,
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }

            // Serving actions
            Column(horizontalAlignment = Alignment.End) {
                if (canServe) {
                    Button(
                        onClick = onServe,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("serve_customer_${customer.name}")
                    ) {
                        Text("Serve", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = "No Stock",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Refuse",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .clickable { onRefuse() }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
fun WholesaleTab(
    gameSave: GameSave,
    viewModel: GameViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        val currentStock = viewModel.getTotalInventoryCount(gameSave)
        val spaceLeft = gameSave.maxStock - currentStock

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Wholesale Market 📦",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Storage: $spaceLeft left",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(ShopItem.values()) { item ->
                val isUnlocked = gameSave.level >= item.unlockLevel
                val stockCount = viewModel.getStockCount(gameSave, item)

                WholesaleItemCard(
                    item = item,
                    isUnlocked = isUnlocked,
                    stockCount = stockCount,
                    hasWholesaleContract = gameSave.hasWholesaleContract,
                    canAffordSingle = gameSave.cash >= item.baseWholesalePrice,
                    canAffordBulk = gameSave.cash >= (item.baseWholesalePrice * 5),
                    spaceLeft = spaceLeft,
                    onBuyOne = { viewModel.buyWholesaleStock(item, 1) },
                    onBuyBulk = { viewModel.buyWholesaleStock(item, 5) }
                )
            }
        }
    }
}

@Composable
fun WholesaleItemCard(
    item: ShopItem,
    isUnlocked: Boolean,
    stockCount: Int,
    hasWholesaleContract: Boolean,
    canAffordSingle: Boolean,
    canAffordBulk: Boolean,
    spaceLeft: Int,
    onBuyOne: () -> Unit,
    onBuyBulk: () -> Unit
) {
    val wholesalePrice = if (hasWholesaleContract) (item.baseWholesalePrice * 0.85f).toInt() else item.baseWholesalePrice
    val markup = ((item.baseRetailPrice - wholesalePrice).toFloat() / wholesalePrice * 100).roundToInt()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isUnlocked) MaterialTheme.colorScheme.outlineVariant else Color.LightGray.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item Emoji Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Text(
                    text = if (isUnlocked) item.emoji else "🔒",
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )

                if (isUnlocked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Cost: ₹$wholesalePrice",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sell: ₹${item.baseRetailPrice}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+$markup% net",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Text(
                        text = "Your Stock: $stockCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    )
                } else {
                    Text(
                        text = "🔓 Unlocks at Level ${item.unlockLevel}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Purchase buy commands
            if (isUnlocked) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Buy 1
                    FilledTonalButton(
                        onClick = onBuyOne,
                        enabled = canAffordSingle && spaceLeft >= 1,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("buy_one_${item.idName}")
                    ) {
                        Text("+1 Wholesale", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Buy 5 (Bulk)
                    Button(
                        onClick = onBuyBulk,
                        enabled = canAffordBulk && spaceLeft >= 5,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("buy_five_${item.idName}")
                    ) {
                        Text("+5 Bulk", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Data holder for shop uprades lists
data class StaticUpgrade(
    val id: String,
    val name: String,
    val description: String,
    val cost: Int,
    val isBought: Boolean
)

@Composable
fun UpgradesTab(
    gameSave: GameSave,
    viewModel: GameViewModel
) {
    val upgrades = listOf(
        StaticUpgrade("stool", "Comfy Stool", "Reduces customer patience decay speed by 40% so they stay longer.", 50, gameSave.hasComfyStool),
        StaticUpgrade("incense", "Aroma Incense", "Fills the air with premium scent! Boosts customer tips/revenue +15%.", 150, gameSave.hasAromaIncense),
        StaticUpgrade("chalk", "Advertising Chalkboard", "Displaying humor deals increases customer spawn rates (+10%).", 300, gameSave.hasAdvertisingChalkboard),
        StaticUpgrade("dog", "Stall Guard Dog", "Adopt a furry companion that automatically catches 40% of sneaky shop thieves.", 500, gameSave.hasStallGuardDog),
        StaticUpgrade("contract", "Wholesale Contract", "Formalizes procurement, reducing wholesale item buy price by 15%.", 1200, gameSave.hasWholesaleContract),
        StaticUpgrade("helper", "Hired Helper", "Automatically serves queuing customer orders every 4s if items are in stock, even offline!", 3000, gameSave.hasHiredHelper),
        StaticUpgrade("cctv", "Security CCTV Camera", "Advanced modern deterrence! Automatically stops 90% of thieves.", 8000, gameSave.hasSecurityCctv)
    )

    val currentPoints = gameSave.cash

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Upgrade Stall 🛠️",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Procure state-of-the-art facilities or helpers to passive play.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(upgrades) { upgrade ->
            UpgradeRow(
                upgrade = upgrade,
                canAfford = currentPoints >= upgrade.cost,
                onBuy = { viewModel.buyUpgrade(upgrade.name, upgrade.cost) }
            )
        }

        // Expansion milestones
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Location Expansions 🏗️",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Build brand properties inside malls & bazaars to compound warehouse size.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val stages = listOf(
            Triple("BAZAAR", 600, 10),
            Triple("BRICK", 2800, 25),
            Triple("SHOWROOM", 12000, 50),
            Triple("MALL", 50000, 80)
        )

        items(stages) { stageInfo ->
            val stageName = stageInfo.first
            val cost = stageInfo.second
            val levelReq = stageInfo.third
            
            val isCurrentOrHigher = when (gameSave.shopStage) {
                "EMPTY_STALL" -> false
                "BAZAAR" -> stageName == "BAZAAR"
                "BRICK" -> stageName == "BAZAAR" || stageName == "BRICK"
                "SHOWROOM" -> stageName != "MALL"
                "MALL" -> true
                else -> false
            }

            val title = viewModel.getStageTitle(stageName)
            val isNextUnlock = when(gameSave.shopStage) {
                "EMPTY_STALL" -> stageName == "BAZAAR"
                "BAZAAR" -> stageName == "BRICK"
                "BRICK" -> stageName == "SHOWROOM"
                "SHOWROOM" -> stageName == "MALL"
                else -> false
            }

            ExpansionRow(
                stageName = title,
                cost = cost,
                levelReq = levelReq,
                isBought = isCurrentOrHigher,
                isLocked = gameSave.level < levelReq,
                isNext = isNextUnlock,
                canAfford = gameSave.cash >= cost,
                onExpand = { viewModel.expandShopStage(stageName, cost, levelReq) }
            )
        }
    }
}

@Composable
fun UpgradeRow(
    upgrade: StaticUpgrade,
    canAfford: Boolean,
    onBuy: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (upgrade.isBought) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = upgrade.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (upgrade.isBought) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Bought",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = upgrade.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (!upgrade.isBought) {
                    Text(
                        text = "Cost: ₹${upgrade.cost}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                }
            }

            if (!upgrade.isBought) {
                Button(
                    onClick = onBuy,
                    enabled = canAfford,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .testTag("buy_upgrade_${upgrade.id}")
                ) {
                    Text("BUY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "ACTIVE",
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExpansionRow(
    stageName: String,
    cost: Int,
    levelReq: Int,
    isBought: Boolean,
    isLocked: Boolean,
    isNext: Boolean,
    canAfford: Boolean,
    onExpand: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isBought) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (isNext) 2.dp else 0.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stageName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                
                if (isBought) {
                    Text("Current properties owned.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Cost: ₹$cost",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Level Req: Lvl $levelReq",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isLocked) MaterialTheme.colorScheme.error else Color.DarkGray,
                                fontWeight = if (isLocked) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }

            if (isBought) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Owned",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                )
            } else if (isNext) {
                Button(
                    onClick = onExpand,
                    enabled = canAfford && !isLocked,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("expand_to_${stageName.replace(" ", "_").lowercase()}")
                ) {
                    Text("EXPAND", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "LOCKED",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun JournalTab(
    logs: List<String>,
    onReset: () -> Unit
) {
    var showResetConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Business Ledger logs 📔",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            TextButton(
                onClick = { showResetConfirmation = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("RESTART ALL", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text("Ledger is completely empty.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(logs) { log ->
                        Text(
                            text = "• $log",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Restart Game Scratch?") },
            text = { Text("Are you absolutely sure you want to trigger a hard wipe? This resets your level back to 1, removes all ₹ cash & upgrades irreversibly!") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        showResetConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("CONFIRM RESET", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
fun ComplaintDialog(
    customer: GameCustomer,
    hasCashForRefund: Boolean = true,
    hasCashForTea: Boolean = true,
    onResolve: (refund: Boolean, freeTea: Boolean) -> Unit
) {
    Dialog(onDismissRequest = { }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "😠 CUSTOMER COMPLAINT!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Black
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(customer.avatarEmoji, fontSize = 36.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "\"${customer.angryComplaint}\"",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Text(
                    text = "- ${customer.name} (wants ${customer.quantity}x ${customer.wantedItem.displayName})",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onErrorContainer)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Pick how you handle this angry customer:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Button(
                    onClick = { onResolve(true, false) },
                    enabled = hasCashForRefund,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apologize & Refund", color = Color.White)
                        Text("(Cost ₹5)", color = Color.White.copy(alpha = 0.8f))
                    }
                }

                Button(
                    onClick = { onResolve(false, true) },
                    enabled = hasCashForTea,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Offer Free Hot Chai ☕", color = Color.White)
                        Text("(Cost ₹2)", color = Color.White.copy(alpha = 0.8f))
                    }
                }

                OutlinedButton(
                    onClick = { onResolve(false, false) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Kick out / Ban customer!", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WinnerTrophyDialog(
    level: Int,
    totalEarnings: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏆 LEVEL 100 CONQUERED! 🏆")
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✨ EMPIRE COMPLETE ✨", fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Congratulations! You started from ₹0 on an empty dirty wooden stall and constructed a retail empire spanning mega malls reaches!",
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Lifetime profits earned: ₹$totalEarnings\nUltimate Status achieved: Level $level Master Shopkeeper",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("HUSTLE CONTINUES!")
            }
        }
    )
}
