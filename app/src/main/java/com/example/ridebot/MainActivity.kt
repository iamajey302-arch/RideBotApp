package com.example.ridebot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.ridebot.bot.RideLogItem
import com.example.ridebot.bot.RideStatus
import java.util.*

val BgDark = Color(0xFF0D0F0E)
val CardDark = Color(0xFF1B1E1C)
val CardBorder = Color(0xFF2B332E)
val PrimaryGreen = Color(0xFF00E676)
val InfoBlue = Color(0xFF29B6F6)
val AlertRed = Color(0xFFFF5252)
val WarningOrange = Color(0xFFFF9100)
val WarningYellow = Color(0xFFFFD600)
val TextGray = Color(0xFF888888)
val InputBg = Color(0xFF242926)

var ttsEngine: TextToSpeech? = null

fun speakFareSummary(context: Context, minFare: Int, maxFare: Int, maxPickup: Double) {
    val speechText = "Bot $minFare rupaye se $maxFare rupaye tak aur $maxPickup kilometer tak ke pickup wali rides accept karega."
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

        ttsEngine = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale("hi", "IN")
            }
        }

        setContent {
            val navController = rememberNavController()
            var showAccessibilityPrompt by remember { mutableStateOf(!isAccessibilityServiceEnabled()) }

            LaunchedEffect(Unit) {
                checkOverlayPermission()
            }

            if (showAccessibilityPrompt) {
                AlertDialog(
                    onDismissRequest = { },
                    containerColor = Color(0xFF1B1E1C),
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Text(
                            text = "⚠️ Accessibility Permission Required",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "RideBot ko Rapido aur Uber rides auto-accept karne ke liye Accessibility Permission zaroori hai.\n\nKripya 'Downloaded Apps' me jakar RideBot ko ON karein.",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                openAccessibilitySettings()
                                showAccessibilityPrompt = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            Text("Enable Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

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

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedServiceName = "$packageName/${RideAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true) ||
                componentName.contains(packageName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
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

    override fun onDestroy() {
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        super.onDestroy()
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
                        Text("Status", color = WarningYellow, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatusIndicator("Accessibility", true) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        StatusIndicator("Overlay", true) {}
                        StatusIndicator("Battery", true) {}
                        StatusIndicator("Float Icon", floatWidgetEnabled) {}
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
    var maxPickupKm by remember { mutableDoubleStateOf(RideAccessibilityService.maxPickupDistanceKm) }

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
                        speakFareSummary(context, minFare, maxFare, maxPickupKm)
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
            IconButton(onClick = { speakFareSummary(context, minFare, maxFare, maxPickupKm) }) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Speak Fare Rules", tint = PrimaryGreen)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = Color(0xFF14241B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { speakFareSummary(context, minFare, maxFare, maxPickupKm) }
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
                    Text("Min Fare", color = PrimaryGreen, fontSize = 12.sp)
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
                    Text("Max Fare", color = PrimaryGreen, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("₹$maxFare", color = PrimaryGreen, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tap to edit", color = TextGray, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 📍 PICKUP DISTANCE TAB (0 km to 5.0 km) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Navigation, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Max Pickup Distance", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text("${String.format("%.1f", maxPickupKm)} km", color = InfoBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = Color(0xFF14222B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Pickup ${String.format("%.1f", maxPickupKm)} km se kam hoga toh accept karega, zyada door hua toh reject.",
                            color = InfoBlue,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Pickup Preset Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.5, 1.0, 2.0, 3.0, 5.0).forEach { km ->
                        val isSelected = (maxPickupKm == km)
                        Button(
                            onClick = {
                                maxPickupKm = km
                                RideAccessibilityService.maxPickupDistanceKm = km
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) InfoBlue else Color(0xFF191C1A),
                                contentColor = if (isSelected) Color.Black else Color.LightGray
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${km}k", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Slider from 0.2 km to 5.0 km
                Slider(
                    value = maxPickupKm.toFloat(),
                    onValueChange = { newValue ->
                        val rounded = (Math.round(newValue * 10.0) / 10.0)
                        maxPickupKm = rounded
                        RideAccessibilityService.maxPickupDistanceKm = rounded
                    },
                    valueRange = 0.2f..5.0f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = InfoBlue,
                        activeTrackColor = InfoBlue,
                        inactiveTrackColor = Color(0xFF2B332E)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
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
fun LogsScreen() {
    val logs = RideAccessibilityService.rideLogs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📋 Ride Activity Logs",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (logs.isNotEmpty()) {
                TextButton(onClick = { logs.clear() }) {
                    Text("Clear All", color = AlertRed, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ListAlt,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Rides Processed Yet", color = TextGray, fontSize = 15.sp)
                    Text("All auto, manual, rejected and missed rides will show here", color = Color(0xFF555555), fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs, key = { it.id }) { item ->
                    val statusColor = when (item.status) {
                        RideStatus.AUTO_ACCEPTED -> PrimaryGreen
                        RideStatus.MANUAL_ACCEPTED -> InfoBlue
                        RideStatus.REJECTED -> AlertRed
                        RideStatus.MISSED -> WarningOrange
                    }

                    val statusBg = when (item.status) {
                        RideStatus.AUTO_ACCEPTED -> Color(0xFF14261B)
                        RideStatus.MANUAL_ACCEPTED -> Color(0xFF14222B)
                        RideStatus.REJECTED -> Color(0xFF261818)
                        RideStatus.MISSED -> Color(0xFF2B2114)
                    }

                    val statusText = when (item.status) {
                        RideStatus.AUTO_ACCEPTED -> "AUTO ACCEPTED"
                        RideStatus.MANUAL_ACCEPTED -> "MANUAL ACCEPTED"
                        RideStatus.REJECTED -> "REJECTED"
                        RideStatus.MISSED -> "MISSED / TIMEOUT"
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = statusBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        when (item.status) {
                                            RideStatus.AUTO_ACCEPTED, RideStatus.MANUAL_ACCEPTED -> Icons.Default.CheckCircle
                                            RideStatus.REJECTED -> Icons.Default.Cancel
                                            RideStatus.MISSED -> Icons.Default.AccessTime
                                        },
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.appName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.timestamp,
                                        color = TextGray,
                                        fontSize = 11.sp
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (item.fare > 0) "₹${item.fare}" else "Offer",
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = statusText,
                                        color = statusColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFF2B332E), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TripOrigin, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Pickup (${item.pickupDist.ifEmpty { "Near" }}): ",
                                        color = PrimaryGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = item.pickupLocation.ifEmpty { "Location detected" },
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = AlertRed, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Drop (${item.dropDist.ifEmpty { "Distance" }}): ",
                                        color = AlertRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = item.dropLocation.ifEmpty { "Location detected" },
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (item.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.note,
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
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
