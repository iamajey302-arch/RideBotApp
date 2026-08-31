package com.example.ridebot

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import com.example.ridebot.bot.RideAccessibilityService

val BgDark = Color(0xFF0D0F0E)
val CardDark = Color(0xFF1B1E1C)
val CardBorder = Color(0xFF2B332E)
val PrimaryGreen = Color(0xFF00E676)
val AlertRed = Color(0xFFFF5252)
val WarningYellow = Color(0xFFFFD600)
val TextGray = Color(0xFF888888)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkOverlayPermission()

        setContent {
            val navController = rememberNavController()
            Scaffold(
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF141715)) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        val items = listOf("Home", "Settings", "Logs", "Auto")
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = {
                                    when (screen) {
                                        "Home" -> Icon(Icons.Default.Home, contentDescription = null)
                                        "Settings" -> Icon(Icons.Default.Settings, contentDescription = null)
                                        "Logs" -> Icon(Icons.Default.List, contentDescription = null)
                                        else -> Icon(Icons.Default.Build, contentDescription = null)
                                    }
                                },
                                label = { Text(screen) },
                                selected = currentRoute == screen,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryGreen,
                                    selectedTextColor = PrimaryGreen,
                                    unselectedIconColor = TextGray,
                                    unselectedTextColor = TextGray,
                                    indicatorColor = Color(0xFF1E2B22)
                                ),
                                onClick = {
                                    navController.navigate(screen) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "Auto",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDark)
                        .padding(innerPadding)
                ) {
                    composable("Home") { HomeScreen() }
                    composable("Settings") { SettingsScreen() }
                    composable("Logs") { LogsScreen() }
                    composable("Auto") { AutoSetupScreen() }
                }
            }
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}

@Composable
fun AutoSetupScreen() {
    var devMode by remember { mutableStateOf(true) }
    var antiBot by remember { mutableStateOf(true) }
    var silenceApps by remember { mutableStateOf(false) }
    var fastMode by remember { mutableStateOf(true) }
    var botActive by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡ RIDE BOT", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("v1.0.0", color = TextGray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Setup Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("⚙ Quick Setup", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Connected", color = WarningYellow, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatusIndicator("Shizuku", true)
                        StatusIndicator("Accessibility", true)
                        StatusIndicator("Overlay", true)
                        StatusIndicator("Battery", true)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bot Safety & Engine
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ToggleRow("Fast Accept (Turbo 0ms)", "Ultra fast click speed", fastMode) {
                        fastMode = it
                        RideAccessibilityService.isFastTurboMode = it
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    ToggleRow("Dev Mode", "Visible & safe to driver apps", devMode) { devMode = it }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    ToggleRow("Anti-Bot Protection", "Human curve gestures & jitter", antiBot) {
                        antiBot = it
                        RideAccessibilityService.isAntiBotEnabled = it
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    ToggleRow("Silence Apps", "Mute partner app alerts", silenceApps) { silenceApps = it }
                }
            }
        }

        // Floating Power Button
        FloatingActionButton(
            onClick = {
                botActive = !botActive
                RideAccessibilityService.isBotRunning = botActive
            },
            containerColor = if (botActive) PrimaryGreen else AlertRed,
            contentColor = Color.Black,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Toggle Bot")
        }
    }
}

@Composable
fun SettingsScreen() {
    var rejectBelow by remember { mutableStateOf(0f) }
    var acceptAbove by remember { mutableStateOf(40f) }

    var rapidoEnabled by remember { mutableStateOf(true) }
    var uberEnabled by remember { mutableStateOf(true) }
    var olaEnabled by remember { mutableStateOf(true) }
    var porterEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("📊 FARE CONTROLS", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Custom ranges from ₹0 to ₹2000", color = TextGray, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("🔴 Reject Below", color = AlertRed, fontWeight = FontWeight.SemiBold)
                    Text("₹${rejectBelow.toInt()}", color = AlertRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Slider(
                    value = rejectBelow,
                    onValueChange = {
                        rejectBelow = it
                        RideAccessibilityService.rejectBelowFare = it.toDouble()
                        if (rejectBelow > acceptAbove) {
                            acceptAbove = rejectBelow
                            RideAccessibilityService.acceptAboveFare = acceptAbove.toDouble()
                        }
                    },
                    valueRange = 0f..2000f,
                    colors = SliderDefaults.colors(thumbColor = AlertRed, activeTrackColor = AlertRed)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("🟢 Accept Above", color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                    Text("₹${acceptAbove.toInt()}", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Slider(
                    value = acceptAbove,
                    onValueChange = {
                        acceptAbove = it
                        RideAccessibilityService.acceptAboveFare = it.toDouble()
                        if (acceptAbove < rejectBelow) {
                            rejectBelow = acceptAbove
                            RideAccessibilityService.rejectBelowFare = rejectBelow.toDouble()
                        }
                    },
                    valueRange = 0f..2000f,
                    colors = SliderDefaults.colors(thumbColor = PrimaryGreen, activeTrackColor = PrimaryGreen)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("🛵 SUPPORTED PLATFORMS", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ToggleRow("Rapido Captain", "Auto take Rapido rides", rapidoEnabled) { rapidoEnabled = it }
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                ToggleRow("Uber Driver", "Auto take Uber trips", uberEnabled) { uberEnabled = it }
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                ToggleRow("Ola Partner", "Auto take Ola rides", olaEnabled) { olaEnabled = it }
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                ToggleRow("Porter Partner", "Auto take Porter orders", porterEnabled) { porterEnabled = it }
            }
        }
    }
}

@Composable
fun StatusIndicator(label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            if (active) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (active) PrimaryGreen else AlertRed,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextGray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryGreen,
                checkedTrackColor = Color(0xFF1E2B22),
                uncheckedThumbColor = TextGray,
                uncheckedTrackColor = Color(0xFF1E2220)
            )
        )
    }
}

@Composable
fun HomeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("⚡ RideBot Online & Running", color = PrimaryGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LogsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("📋 No Logs Recorded", color = TextGray, fontSize = 15.sp)
    }
}
