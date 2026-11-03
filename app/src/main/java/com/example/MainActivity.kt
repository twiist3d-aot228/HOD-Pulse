package com.example

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DayZServerRepository
import com.example.data.DayZServerStatus
import com.example.data.DayZTimeHelper
import com.example.ui.theme.DayZServerTheme
import com.example.ui.theme.MilitaryBackground
import com.example.ui.theme.MilitaryError
import com.example.ui.theme.MilitaryOutline
import com.example.ui.theme.MilitaryPrimary
import com.example.ui.theme.MilitarySecondary
import com.example.ui.theme.MilitarySurface
import com.example.ui.theme.MilitarySurfaceVariant
import com.example.ui.theme.MilitaryTertiary
import com.example.ui.theme.MilitaryTextMuted
import com.example.ui.theme.MilitaryTextPrimary
import com.example.ui.theme.MilitaryTextSecondary
import com.example.widget.DayZServerWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize repository from cache
        DayZServerRepository.instance.initFromCache(applicationContext)

        setContent {
            DayZServerTheme {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MilitaryBackground
                ) { innerPadding ->
                    DayZServerDashboard(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DayZServerDashboard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { DayZServerRepository.instance }
    val scope = rememberCoroutineScope()

    val status by repository.statusFlow.collectAsState()
    val isRefreshing by repository.isRefreshing.collectAsState()

    var secondsUntilAutoRefresh by remember { mutableIntStateOf(60) }
    var restartRemainingMs by remember { mutableLongStateOf(DayZTimeHelper.getRestartCountdownMs()) }
    var showTelemetry by remember { mutableStateOf(false) }

    // 1-second ticker for restart countdown and auto-refresh countdown
    LaunchedEffect(Unit) {
        // Initial query on start
        repository.refresh(context)
        secondsUntilAutoRefresh = 60

        while (true) {
            delay(1000L)
            restartRemainingMs = DayZTimeHelper.getRestartCountdownMs()
            if (secondsUntilAutoRefresh > 1) {
                secondsUntilAutoRefresh -= 1
            } else {
                secondsUntilAutoRefresh = 60
                repository.refresh(context)
            }
        }
    }

    // Pulse animation for radar beacon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    // Subtle continuous rotation animation for refresh icon while data is being fetched
    val refreshRotation = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (isActive) {
                refreshRotation.animateTo(
                    targetValue = refreshRotation.value + 360f,
                    animationSpec = tween(durationMillis = 900, easing = LinearEasing)
                )
            }
        } else if (refreshRotation.value > 0f) {
            // Smoothly complete the remaining turn to upright 360-degree multiple
            val nextFullTurn = (((refreshRotation.value / 360f).toInt() + 1) * 360).toFloat()
            refreshRotation.animateTo(
                targetValue = nextFullTurn,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
            refreshRotation.snapTo(0f)
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MilitaryBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Top Tactical HUD Bar
            TacticalTopBar(
                isOnline = status.isOnline,
                isRefreshing = isRefreshing,
                beaconAlpha = beaconAlpha,
                rotationAngle = refreshRotation.value,
                onRefreshClick = {
                    scope.launch {
                        secondsUntilAutoRefresh = 60
                        repository.refresh(context)
                    }
                }
            )

            // Server Hero Card
            ServerHeroCard(
                status = status,
                beaconAlpha = beaconAlpha,
                onCopyIp = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("DayZ Server IP", "198.73.57.157:2303")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "IP 198.73.57.157:2303 copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            )

            // 4-Card Tactical Grid
            TacticalMetricsGrid(
                status = status,
                restartCountdownMs = restartRemainingMs
            )

            // Auto-Refresh & Force Refresh Controls Card
            AutoRefreshControlsCard(
                secondsUntilRefresh = secondsUntilAutoRefresh,
                isRefreshing = isRefreshing,
                rotationAngle = refreshRotation.value,
                lastUpdatedMillis = status.lastUpdatedMillis,
                onForceRefresh = {
                    scope.launch {
                        secondsUntilAutoRefresh = 60
                        repository.refresh(context)
                    }
                }
            )

            // Home Screen Widget Setup Helper
            WidgetPromoCard(
                onPinWidget = {
                    pinWidgetToHomeScreen(context)
                }
            )

            // Telemetry & Keywords Panel
            TelemetryCard(
                status = status,
                isExpanded = showTelemetry,
                onToggleExpand = { showTelemetry = !showTelemetry }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TacticalTopBar(
    isOnline: Boolean,
    isRefreshing: Boolean,
    beaconAlpha: Float,
    rotationAngle: Float,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tactical Radar Beacon indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MilitarySurfaceVariant)
                    .border(1.dp, MilitaryOutline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) MilitaryPrimary.copy(alpha = beaconAlpha)
                            else MilitaryError.copy(alpha = beaconAlpha)
                        )
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "HOD PULSE",
                    style = MaterialTheme.typography.titleLarge,
                    color = MilitaryTextPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (isOnline) "TACTICAL RADAR // LIVE LINK ESTABLISHED" else "TACTICAL RADAR // LINK DISRUPTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOnline) MilitarySecondary else MilitaryError
                )
            }
        }

        IconButton(
            onClick = onRefreshClick,
            enabled = !isRefreshing,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MilitarySurfaceVariant)
                .border(
                    1.dp,
                    if (isRefreshing) MilitaryPrimary else MilitaryOutline,
                    CircleShape
                )
                .testTag("refresh_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.refresh_action),
                tint = if (isRefreshing) MilitaryPrimary else MilitarySecondary,
                modifier = Modifier.rotate(rotationAngle)
            )
        }
    }
}

@Composable
private fun ServerHeroCard(
    status: DayZServerStatus,
    beaconAlpha: Float,
    onCopyIp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MilitarySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MilitaryOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Status pill and Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (status.isOnline) MilitarySurfaceVariant else Color(0xFF2A1212),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (status.isOnline) MilitaryPrimary else MilitaryError
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (status.isOnline) MilitaryPrimary.copy(alpha = beaconAlpha)
                                    else MilitaryError
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (status.isOnline) "SERVER ONLINE // PVE" else "SERVER RESTARTING OR OFFLINE",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (status.isOnline) MilitaryPrimary else MilitaryError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Ping latency
                if (status.isOnline && status.pingMs > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MilitarySecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${status.pingMs} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MilitarySecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Server Name
            Text(
                text = status.serverName,
                style = MaterialTheme.typography.titleLarge,
                color = MilitaryTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // IP & Copy Button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MilitarySurfaceVariant)
                    .border(1.dp, MilitaryOutline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "DIRECT CONNECT ADDRESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MilitaryTextMuted
                    )
                    Text(
                        text = "198.73.57.157:2303 (UDP)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MilitaryPrimary
                    )
                }

                IconButton(
                    onClick = onCopyIp,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MilitaryBackground)
                        .testTag("copy_ip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy IP",
                        tint = MilitarySecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TacticalMetricsGrid(
    status: DayZServerStatus,
    restartCountdownMs: Long
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: PLAYERS & RESTART IN
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Players Card
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "PLAYERS ONLINE",
                value = if (status.isOnline) "${status.players} / ${status.maxPlayers}" else "-- / ${status.maxPlayers}",
                valueColor = if (status.isOnline) MilitaryPrimary else MilitaryTextMuted,
                subtext = if (status.isOnline) "${status.occupancyPercent}% CAPACITY" else "DISCONNECTED",
                icon = {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = MilitarySecondary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                extraContent = {
                    LinearProgressIndicator(
                        progress = {
                            if (status.maxPlayers > 0 && status.isOnline)
                                (status.players.toFloat() / status.maxPlayers).coerceIn(0f, 1f)
                            else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MilitaryPrimary,
                        trackColor = MilitarySurfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            )

            // RESTART IN Card
            val isRestartSoon = restartCountdownMs < 10 * 60 * 1000L // under 10 min
            val countdownStr = DayZTimeHelper.formatCountdown(restartCountdownMs)

            MetricCard(
                modifier = Modifier.weight(1f),
                title = "RESTART IN",
                value = countdownStr,
                valueColor = if (isRestartSoon) MilitaryError else MilitaryTertiary,
                subtext = "EVERY 3 HRS ON THE HOUR",
                icon = {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = if (isRestartSoon) MilitaryError else MilitaryTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                extraContent = {
                    // 3 hours = 10,800,000 ms
                    val progressFraction = (1f - (restartCountdownMs.toFloat() / (3f * 3600f * 1000f))).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isRestartSoon) MilitaryError else MilitaryTertiary,
                        trackColor = MilitarySurfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            )
        }

        // Row 2: MAP & IN-GAME TIME
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // MAP Card
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "MAP",
                value = status.map,
                valueColor = MilitaryTextPrimary,
                subtext = "CHERNARUS POST-SOVIET",
                icon = {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = MilitarySecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            // IN-GAME TIME Card
            val timeDisplay = if (status.isOnline) {
                if (status.inGamePhase.isNotBlank()) "${status.inGameTime} (${status.inGamePhase})" else status.inGameTime
            } else {
                "--:--"
            }

            MetricCard(
                modifier = Modifier.weight(1f),
                title = "IN-GAME TIME",
                value = timeDisplay,
                valueColor = MilitarySecondary,
                subtext = "EXTRACTED VIA A2S ETM",
                icon = {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MilitarySecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    valueColor: Color,
    subtext: String,
    icon: @Composable () -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MilitarySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MilitaryOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MilitaryTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )

            if (extraContent != null) {
                Spacer(modifier = Modifier.height(8.dp))
                extraContent()
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = MilitaryTextMuted,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun AutoRefreshControlsCard(
    secondsUntilRefresh: Int,
    isRefreshing: Boolean,
    rotationAngle: Float,
    lastUpdatedMillis: Long,
    onForceRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MilitarySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MilitaryOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AUTO-REFRESH: ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MilitaryTextSecondary
                    )
                    Text(
                        text = "${secondsUntilRefresh}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MilitaryPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "LAST SYNC: " + DayZTimeHelper.formatTimestamp(lastUpdatedMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MilitaryTextMuted
                )
            }

            Button(
                onClick = onForceRefresh,
                enabled = !isRefreshing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MilitaryPrimary,
                    contentColor = MilitaryBackground
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("force_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotationAngle),
                    tint = MilitaryBackground
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRefreshing) "FETCHING..." else "FORCE REFRESH",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun WidgetPromoCard(
    onPinWidget: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MilitarySurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MilitaryOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = MilitaryPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HOME SCREEN WIDGET (4x2 RESIZABLE)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MilitaryTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Glance at live players, in-game time, map, and the 3-hour restart countdown directly from your Android home screen. Tap the widget anytime for instant live sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MilitaryTextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onPinWidget,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_widget_button"),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MilitaryPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MilitaryPrimary)
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ADD WIDGET TO HOME SCREEN",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    status: DayZServerStatus,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MilitarySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MilitaryOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "STEAM A2S PROTOCOL TELEMETRY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MilitaryTextSecondary,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MilitaryTextSecondary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = MilitaryOutline, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    TelemetryItem(label = "Protocol", value = "Steam Source Engine Query (A2S_INFO)")
                    TelemetryItem(label = "Query Port", value = "2303 UDP")
                    TelemetryItem(label = "Game Port", value = "2302 UDP")
                    TelemetryItem(label = "Round Trip Time", value = "${status.pingMs} ms")
                    TelemetryItem(
                        label = "Raw A2S Keywords",
                        value = if (status.rawKeywords.isNotBlank()) status.rawKeywords else "(None / Offline)"
                    )
                    TelemetryItem(label = "Restart Cycle", value = "Every 3 hours on the hour (00, 03, 06, 09, 12, 15, 18, 21 UTC)")
                }
            }
        }
    }
}

@Composable
private fun TelemetryItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MilitaryTextMuted,
            fontSize = 9.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MilitaryTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}

private fun pinWidgetToHomeScreen(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
        val myProvider = ComponentName(context, DayZServerWidgetProvider::class.java)
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            appWidgetManager.requestPinAppWidget(myProvider, null, null)
            Toast.makeText(context, "Pinned widget request sent to launcher!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Long-press your home screen, select Widgets, and choose 'HOD Pulse'", Toast.LENGTH_LONG).show()
        }
    } else {
        Toast.makeText(context, "Long-press your home screen, select Widgets, and choose 'HOD Pulse'", Toast.LENGTH_LONG).show()
    }
}
