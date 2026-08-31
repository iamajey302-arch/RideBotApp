package com.example.ridebot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import com.example.ridebot.bot.FloatingWidgetService
import com.example.ridebot.bot.RideAccessibilityService
import java.util.*

val BgDark = Color(0xFF0D0F0E)
val CardDark = Color(0xFF1B1E1C)
val CardBorder = Color(0xFF2B332E)
val PrimaryGreen = Color(0xFF00E676)
val AlertRed = Color(0xFFFF5252)
val WarningYellow = Color(0xFFFFD600)
val TextGray = Color(0xFF888888)
val InputBg = Color(0xFF242926)

var ttsEngine: TextToSpeech? = null

fun speakFareSummary(context: Context, minFare: Int, maxFare: Int) {
    val speechText = "Bot $minFare rupaye se lekar $maxFare rupaye tak ki saari rides accept karega, aur is range ke bahar ki rides reject ho jayengi."
    if (ttsEngine == null) {
        ttsEngine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale("hi", "IN")
                ttsEngine?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "FareAudio")
            }
        }
    } else {
        ttsEngine?.language = Locale("hi", "IN")
        ttsEngine?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "FareAudio")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkOverlayPermission()

        ttsEngine = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale("hi", "IN")
            }
        }

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

    override fun onDestroy() {
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        super.onDestroy()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            startService(Intent(this, FloatingWidgetService::class.java))
        }
    }
}

@Composable
fun AutoSetupScreen() {
    val context = LocalContext.current
    var devMode by remember { mutableStateOf(true) }
    var antiBot by remember { mutableStateOf(RideAccessibilityService.isAntiBotEnabled) }
    var silenceApps by remember { mutableStateOf(false) }
    var fastMode by remember { mutableStateOf(RideAccessibilityService.isFastTurboMode) }
    var botActive by remember { mutableStateOf(RideAccessibilityService.isBotRunning) }
    var floatWidgetEnabled by remember { mutableStateOf(true) }

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
                        StatusIndicator("Accessibility", true)
                        StatusIndicator("Overlay", true)
                        StatusIndicator("Battery", true)
                        StatusIndicator("Float Icon", floatWidgetEnabled)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ToggleRow("Floating Widget", "Show draggable floating power button", floatWidgetEnabled) {
                        floatWidgetEnabled = it
                        if (it) {
                            context.startService(Intent(context, FloatingWidgetService::class.java))
                        } else {
                            context.stopService(Intent(context, FloatingWidgetService::class.java))
                        }
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
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
    val context = LocalContext.current
    var minFare by remember { mutableIntStateOf(RideAccessibilityService.minTargetFare.toInt()) }
    var maxFare by remember { mutableIntStateOf(RideAccessibilityService.maxTargetFare.toInt()) }

    var showDialogFor by remember { mutableStateOf<String?>(null) }
    var tempFareText by remember { mutableStateOf("") }

    var isIncludeMode by remember { mutableStateOf(true) }
    var customAreaText by remember { mutableStateOf("") }
    val selectedAreas = remember { mutableStateListOf<String>() }

    val quickAddList = listOf(
        "Delhi", "New Delhi", "Noida", "Greater Noida",
        "Gurgaon", "Gurugram", "Ghaziabad", "Faridabad",
        "Narela", "Sonipat", "Manesar", "Airport"
    )

    if (showDialogFor != null) {
        val isMin = showDialogFor == "min"
        val title = if (isMin) "Minimum Fare Limit" else "Maximum Fare Limit"

        AlertDialog(
            onDismissRequest = { showDialogFor = null },
            containerColor = Color(0xFF1B1E1C),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = tempFareText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 5) {
                            tempFareText = input
                        }
                    },
                    prefix = { Text("₹ ", color = PrimaryGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = CardBorder,
                        cursorColor = PrimaryGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = tempFareText.toIntOrNull() ?: 1
                        if (isMin) {
                            minFare = value
                            RideAccessibilityService.minTargetFare = value.toDouble()
                        } else {
                            maxFare = value
                            RideAccessibilityService.maxTargetFare = value.toDouble()
                        }
                        speakFareSummary(context, minFare, maxFare)
                        showDialogFor = null
                    }
                ) {
                    Text("Set", color = PrimaryGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialogFor = null }) {
                    Text("Cancel", color = TextGray, fontSize = 16.sp)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // --- FARE CONTROLS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("₹ Target Fare Range", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = { speakFareSummary(context, minFare, maxFare) }) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Speak Fare Rules", tint = PrimaryGreen)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = Color(0xFF14241B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { speakFareSummary(context, minFare, maxFare) }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Accepts ₹$minFare se ₹$maxFare tak ki rides (Baki reject)",
                    color = PrimaryGreen,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14261B)),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        tempFareText = minFare.toString()
                        showDialogFor = "min"
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Min Fare (Starting)", color = PrimaryGreen, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("₹$minFare", color = PrimaryGreen, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tap to edit", color = TextGray, fontSize = 10.sp)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14261B)),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        tempFareText = maxFare.toString()
                        showDialogFor = "max"
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Max Fare (Limit)", color = PrimaryGreen, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("₹$maxFare", color = PrimaryGreen, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tap to edit", color = TextGray, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- AREA FILTERS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Area Filters", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = Color(0xFF14241B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isIncludeMode) "Include areas accept karega, baki ignore." else "Exclude areas reject karega.",
                            color = PrimaryGreen,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isIncludeMode = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isIncludeMode) Color(0xFF1A3324) else Color(0xFF191C1A),
                            contentColor = if (isIncludeMode) PrimaryGreen else TextGray
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Include", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { isIncludeMode = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isIncludeMode) Color(0xFF331A1A) else Color(0xFF191C1A),
                            contentColor = if (!isIncludeMode) AlertRed else TextGray
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exclude", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customAreaText,
                        onValueChange = { customAreaText = it },
                        placeholder = { Text("Type area name...", color = TextGray, fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (customAreaText.isNotBlank() && !selectedAreas.contains(customAreaText.trim())) {
                                selectedAreas.add(customAreaText.trim())
                                customAreaText = ""
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF1E2B22), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Area", tint = PrimaryGreen)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedAreas.isNotEmpty()) {
                    Text("Added Areas (${selectedAreas.size}):", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(selectedAreas) { area ->
                            Surface(
                                color = if (isIncludeMode) Color(0xFF1A3324) else Color(0xFF331A1A),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.clickable { selectedAreas.remove(area) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(area, color = if (isIncludeMode) PrimaryGreen else AlertRed, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextGray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Text("No areas added yet", color = TextGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text("⊕ Quick add", color = TextGray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))

                val chunkedChips = quickAddList.chunked(3)
                chunkedChips.forEach { rowChips ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowChips.forEach { chip ->
                            Surface(
                                color = Color(0xFF14241B),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .clickable {
                                        if (!selectedAreas.contains(chip)) {
                                            selectedAreas.add(chip)
                                        }
                                    }
                            ) {
                                Text(
                                    "+ $chip",
                                    color = PrimaryGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- PLATFORMS ---
        Text("🛵 SUPPORTED PLATFORMS", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ToggleRow("Rapido Captain", "Auto take Rapido rides", true) {}
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                ToggleRow("Uber Driver", "Auto take Uber trips", true) {}
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                ToggleRow("Ola Partner", "Auto take Ola rides", true) {}
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                ToggleRow("Porter Partner", "Auto take Porter orders", true) {}
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
