package com.example.rtrab_c

// --- Serialization Import ---
import kotlinx.serialization.Serializable

// --- Supabase Imports ---
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.*

// --- Android & System Imports ---
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

// --- New Image Imports ---
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog

// --- OsmDroid Imports ---
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker


// Helper data class to fetch only the emails of banned users
@Serializable
data class BannedUserDto(val email: String)

// ==========================================
// SCREEN: MODERATOR DASHBOARD
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeratorDashboardScreen(
    supabase: SupabaseClient,
    onNavigateHome: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var allReports by remember { mutableStateOf(listOf<HazardReport>()) }
    var showAnalyticsDialog by remember { mutableStateOf(false) }
    var showAuditDialog by remember { mutableStateOf(false) }
    var mapReference by remember { mutableStateOf<MapView?>(null) }

    // --- MENU & PROFILE STATES ---
    var showMenu by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var currentUserProfile by remember { mutableStateOf<FullUserProfile?>(null) }

    // --- HISTORY & FILTER STATES ---
    var activeTab by remember { mutableStateOf("ACTIVE") }
    var selectedHazardFilter by remember { mutableStateOf("ALL") }
    val hazardCategories = listOf("ALL", "Flood", "Fire", "Landslide", "Accident", "Other")

    val currentUserId = supabase.auth.currentUserOrNull()?.id ?: ""
    val currentUserEmail = supabase.auth.currentUserOrNull()?.email ?: "No Email"

    LaunchedEffect(Unit) {
        try {
            allReports = supabase.from("reports")
                .select()
                .decodeList<HazardReport>()
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load reports: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Fetch the full ERD user profile for moderator
        if (currentUserId.isNotEmpty()) {
            try {
                val userData = supabase.from("users")
                    .select { filter { eq("id", currentUserId) } }
                    .decodeSingleOrNull<FullUserProfile>()
                currentUserProfile = userData
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Map Lifecycle Handling
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapReference?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapReference?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Dynamic Stats
    val totalReports = allReports.size
    val pendingReports = allReports.count { it.status == "PENDING" }
    val verifiedReports = allReports.count { it.status == "VERIFIED" }
    val resolvedReports = allReports.count { it.status == "RESOLVED" }
    val rejectedReports = allReports.count { it.status == "REJECTED" }

    // --- FILTER LOGIC ---
    val displayedReports = allReports.filter { report ->
        val matchesTab = if (activeTab == "ACTIVE") {
            report.status == "PENDING" || report.status == "VERIFIED"
        } else {
            report.status == "RESOLVED" || report.status == "REJECTED"
        }

        val matchesHazard = if (selectedHazardFilter == "ALL") {
            true
        } else {
            report.type.equals(selectedHazardFilter, ignoreCase = true)
        }

        matchesTab && matchesHazard
    }

    LazyColumn(Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .background(Color.White)
        .padding(16.dp)) {
        item {
            // --- TOP HEADER ROW WITH 3-DOTS MENU ---
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.rtrab_logo), contentDescription = "Logo", modifier = Modifier.size(70.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("RTRAB-C", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("Baguio Real-Time Risk\nAssessment", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp)
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu Options", tint = Color.DarkGray)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("👤 View Profile", fontWeight = FontWeight.Bold) },
                            onClick = { showMenu = false; showProfileDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("🔑 Reset Password", fontWeight = FontWeight.Bold) },
                            onClick = {
                                showMenu = false
                                Toast.makeText(context, "Reset Password feature coming soon!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("🚪 Logout", color = Color.Red, fontWeight = FontWeight.ExtraBold) },
                            onClick = { showMenu = false; onLogout() }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showAnalyticsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(45.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📊", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ANALYTICS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = { showAuditDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(45.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("USER AUDIT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Navigation", color = Color(0xFF00897B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onNavigateHome,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF154360)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                    Text("Home", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Return to Live Map", color = Color.White, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DashboardStatBox("Total", totalReports, Color(0xFFE0E0E0), Color.Black, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(4.dp))
                DashboardStatBox("Pending", pendingReports, Color(0xFFD4C439), Color.White, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(4.dp))
                DashboardStatBox("Fixed", resolvedReports, Color(0xFF2E7D32), Color.White, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(4.dp))
                DashboardStatBox("Spam", rejectedReports, Color.DarkGray, Color.White, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Moderator Dashboard", color = Color(0xFF00897B), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("Review and verify community submitted hazard reports", color = Color.DarkGray, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // MODERATOR TACTICAL MAP
            // ==========================================
            Text("TACTICAL MAP VIEW", color = Color(0xFF154360), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { mapContext ->

                        val osmConfig = org.osmdroid.config.Configuration.getInstance()
                        val prefs = mapContext.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
                        osmConfig.load(mapContext, prefs)
                        osmConfig.userAgentValue = "RTRABC_Baguio_App/1.0"

                        object : MapView(mapContext) {
                            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                                when (ev.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> parent.requestDisallowInterceptTouchEvent(true)
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
                                }
                                return super.dispatchTouchEvent(ev)
                            }
                        }.apply {

                            // --- USE CARTO POSITRON FOR A CLEAN, HIGH-RES DASHBOARD MAP ---
                            setTileSource(
                                org.osmdroid.tileprovider.tilesource.XYTileSource(
                                    "CartoPositron",
                                    1,
                                    20,
                                    256,
                                    ".png",
                                    arrayOf("https://cartodb-basemaps-a.global.ssl.fastly.net/light_all/")
                                )
                            )
                            setMultiTouchControls(true)
                            controller.setZoom(14.0)
                            controller.setCenter(GeoPoint(16.4164, 120.5930)) // Centered on Baguio
                            mapReference = this
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()
                        allReports.filter { it.status == "PENDING" || it.status == "VERIFIED" }.forEach { report ->
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(report.latitude, report.longitude)
                            marker.title = "[${report.status}] ${report.type}"
                            marker.snippet = report.description
                            marker.icon = createColoredMarker(context, report.type)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            mapView.overlays.add(marker)
                        }
                        mapView.invalidate()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // TABS & FILTERS FOR DATABASE
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).height(56.dp).clickable { activeTab = "ACTIVE" },
                    colors = CardDefaults.cardColors(
                        containerColor = if (activeTab == "ACTIVE") Color(0xFF154360) else Color(0xFFF5F5F5)
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Active Reports", color = if (activeTab == "ACTIVE") Color.White else Color.DarkGray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Pending & Verified", color = if (activeTab == "ACTIVE") Color.LightGray else Color.Gray, fontSize = 10.sp)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).height(56.dp).clickable { activeTab = "HISTORY" },
                    colors = CardDefaults.cardColors(
                        containerColor = if (activeTab == "HISTORY") Color(0xFFE67E22) else Color(0xFFF5F5F5)
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("History Logs", color = if (activeTab == "HISTORY") Color.White else Color.DarkGray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Accomplished & Closed", color = if (activeTab == "HISTORY") Color.White.copy(alpha=0.8f) else Color.Gray, fontSize = 10.sp)
                    }
                }
            }

            Text(
                text = if (activeTab == "ACTIVE") "CITY REPORT DATABASE" else "ACCOMPLISHED HISTORY",
                color = Color(0xFFE53935),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(hazardCategories) { category ->
                    val isSelected = selectedHazardFilter == category
                    Surface(
                        modifier = Modifier.clickable { selectedHazardFilter = category },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF0F8C3B) else Color(0xFFE0E0E0),
                        contentColor = if (isSelected) Color.White else Color.DarkGray
                    ) {
                        Text(
                            text = category.uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (displayedReports.isEmpty()) {
            item {
                Text(
                    text = "No ${if (activeTab == "ACTIVE") "active" else "archived"} reports match your filter.",
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(displayedReports) { report ->

                var showFullImage by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable {
                        mapReference?.controller?.animateTo(GeoPoint(report.latitude, report.longitude))
                        mapReference?.controller?.setZoom(18.0)
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {

                        val hazardIcon = when (report.type) {
                            "Flood" -> "🌊"
                            "Fire" -> "🔥"
                            "Landslide" -> "🪨"
                            "Accident" -> "💥"
                            "Other" -> "⚠️"
                            else -> "⚠️"
                        }

                        val statusColor = when (report.status) {
                            "PENDING" -> Color.Red
                            "VERIFIED" -> Color(0xFFF57C00)
                            "RESOLVED" -> Color(0xFF4CAF50)
                            else -> Color.Gray // REJECTED
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$hazardIcon [${report.type.uppercase()}]", fontWeight = FontWeight.ExtraBold, color = Color(0xFF154360))
                            Text("👍 ${report.upvotes ?: 0} Citizens Confirmed", fontSize = 10.sp, color = Color(0xFF00796B), fontWeight = FontWeight.Bold)
                        }

                        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
                        val formattedTime = sdf.format(java.util.Date(report.timestamp))

                        Text(
                            text = formattedTime,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                            if (!report.imageUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = report.imageUrl,
                                    contentDescription = "Hazard Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showFullImage = true }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Column(Modifier.weight(1f)) {
                                Text(report.description, fontSize = 15.sp, color = Color.DarkGray)

                                if (report.address.isNotEmpty()) {
                                    Text("📍 ${report.address}", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Reported by: ${report.reporterEmail}", color = Color.Gray, fontSize = 12.sp)
                                Text("Status: ${report.status}", color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                                Spacer(modifier = Modifier.height(12.dp))

                                // ==========================================
                                // ROW 1: PRIMARY ACTION BUTTONS
                                // ==========================================
                                if (report.status == "PENDING") {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        supabase.from("reports").update({ set("status", "VERIFIED") }) {
                                                            filter { eq("id", report.id ?: "") }
                                                        }
                                                        allReports = allReports.map { if (it.id == report.id) it.copy(status = "VERIFIED") else it }
                                                        Toast.makeText(context, "City Dispatched!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) { Text("VERIFY & DISPATCH", fontWeight = FontWeight.Bold, fontSize = 10.sp) }

                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        supabase.from("reports").update({ set("status", "REJECTED") }) {
                                                            filter { eq("id", report.id ?: "") }
                                                        }
                                                        allReports = allReports.map { if (it.id == report.id) it.copy(status = "REJECTED") else it }
                                                        Toast.makeText(context, "Prank/Spam Rejected!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF757575)),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) { Text("REJECT", fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                                    }
                                } else if (report.status == "VERIFIED") {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    supabase.from("reports").update({ set("status", "RESOLVED") }) {
                                                        filter { eq("id", report.id ?: "") }
                                                    }
                                                    allReports = allReports.map { if (it.id == report.id) it.copy(status = "RESOLVED") else it }
                                                    Toast.makeText(context, "Issue Resolved!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(4.dp)
                                    ) { Text("MARK AS RESOLVED (FIXED)", fontWeight = FontWeight.Bold) }
                                } else {
                                    Box(Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                                        Text("🔒 This report has been closed and archived.", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }

                                // ==========================================
                                // ROW 2: NAVIGATION BUTTON
                                // ==========================================
                                if (report.status == "PENDING" || report.status == "VERIFIED") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val gmmIntentUri = Uri.parse("google.navigation:q=${report.latitude},${report.longitude}")
                                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                            mapIntent.setPackage("com.google.android.apps.maps")
                                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                                context.startActivity(mapIntent)
                                            } else {
                                                Toast.makeText(context, "Google Maps is not installed.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE3F2FD)),
                                        modifier = Modifier.fillMaxWidth().height(35.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("NAVIGATE TO HAZARD", color = Color(0xFF1565C0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (showFullImage && !report.imageUrl.isNullOrEmpty()) {
                            Dialog(onDismissRequest = { showFullImage = false }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showFullImage = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = report.imageUrl,
                                        contentDescription = "Full Size Photo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAnalyticsDialog) {
        val totalFloods = allReports.count { it.type == "Flood" }
        val totalFires = allReports.count { it.type == "Fire" }
        val totalLandslides = allReports.count { it.type == "Landslide" }
        val spamPercentage = if (totalReports > 0) (rejectedReports * 100) / totalReports else 0

        AlertDialog(
            onDismissRequest = { showAnalyticsDialog = false },
            title = { Text("LGU Systems Report", fontWeight = FontWeight.ExtraBold, color = Color(0xFF154360)) },
            text = {
                Column {
                    Text("Official data breakdown for the City of Baguio.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Top Hazards Reported:", fontWeight = FontWeight.Bold)
                    Text("🌊 Flooding Incidents: $totalFloods")
                    Text("🔥 Fire Incidents: $totalFires")
                    Text("🪨 Landslides: $totalLandslides")

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("System Integrity:", fontWeight = FontWeight.Bold)
                    Text("Spam / Prank Rate: $spamPercentage%", color = if(spamPercentage > 20) Color.Red else Color.Black)
                    Text("Issues Successfully Fixed: $resolvedReports")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Toast.makeText(context, "Report Exported to City Database", Toast.LENGTH_SHORT).show()
                    showAnalyticsDialog = false
                }) {
                    Text("EXPORT TO CSV", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnalyticsDialog = false }) { Text("Close") }
            }
        )
    }

    // ==========================================
    // BRAND NEW AUDIT DIALOG: TABS & FILTERING
    // ==========================================
    if (showAuditDialog) {
        data class AuditRecord(val id: String, val email: String, val total: Int, val spam: Int, val firstDate: Long)

        var auditTab by remember { mutableStateOf("ACTIVE") } // "ACTIVE" or "BANNED"
        var databaseBannedEmails by remember { mutableStateOf(setOf<String>()) }
        var newlyBannedEmails by remember { mutableStateOf(setOf<String>()) }
        var newlyUnbannedEmails by remember { mutableStateOf(setOf<String>()) }
        var isLoadingBanned by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            try {
                // Fetch emails of all users who are currently banned in the database
                val bannedList = supabase.from("users")
                    .select {
                        filter { eq("role", "BANNED") }
                    }.decodeList<BannedUserDto>()

                databaseBannedEmails = bannedList.map { it.email }.toSet()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingBanned = false
            }
        }

        val userStats = allReports.groupBy { it.reporterEmail }.mapNotNull { (email, reports) ->
            val reporterId = reports.firstOrNull()?.reporterId ?: ""
            val total = reports.size
            val spamCount = reports.count { it.status == "REJECTED" }
            val firstDate = reports.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()

            AuditRecord(reporterId, email ?: "Unknown User", total, spamCount, firstDate)
        }.sortedByDescending { it.spam }

        // Determine who is currently banned based on DB state + local session toggles
        val currentBannedEmails = (databaseBannedEmails + newlyBannedEmails) - newlyUnbannedEmails

        // Filter the list based on the selected tab
        val displayedUsers = if (auditTab == "ACTIVE") {
            userStats.filter { !currentBannedEmails.contains(it.email) }
        } else {
            userStats.filter { currentBannedEmails.contains(it.email) }
        }

        AlertDialog(
            onDismissRequest = { showAuditDialog = false },
            title = { Text("Citizen Trust & Audit Log", fontWeight = FontWeight.ExtraBold, color = Color(0xFF154360)) },
            text = {
                // fillMaxHeight constraints prevent the modal from pushing the Close button offscreen
                Column(Modifier.fillMaxHeight(0.85f)) {
                    Text("Review citizen reports and manage account access.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- TAB BUTTONS ---
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { auditTab = "ACTIVE" },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (auditTab == "ACTIVE") Color(0xFF154360) else Color(0xFFE0E0E0)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("ACTIVE", color = if (auditTab == "ACTIVE") Color.White else Color.DarkGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { auditTab = "BANNED" },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (auditTab == "BANNED") Color(0xFFC62828) else Color(0xFFE0E0E0)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("BANNED", color = if (auditTab == "BANNED") Color.White else Color.DarkGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoadingBanned) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF154360))
                        }
                    } else if (displayedUsers.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(if (auditTab == "ACTIVE") "No active users to display." else "No banned accounts.", color = Color.Gray)
                        }
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            displayedUsers.forEach { record ->
                                Card(
                                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (auditTab == "BANNED") Color(0xFFFFEBEE) else if (record.spam > 2) Color(0xFFFFF3E0) else Color(0xFFF5F5F5)
                                    )
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        // TOP ROW: Details
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text("👤 ${record.email}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)

                                                val sdf = SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                                val dateStr = sdf.format(java.util.Date(record.firstDate))

                                                Text("Active since: $dateStr", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Total: ${record.total}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                                Text("Spam: ${record.spam}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (record.spam > 0) Color.Red else Color.DarkGray)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // BOTTOM ROW: Block or Unblock Buttons based on Tab
                                        if (auditTab == "ACTIVE") {
                                            if (record.spam > 0) {
                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            if (record.id.isBlank()) {
                                                                Toast.makeText(context, "Cannot block: This is an old report missing a User ID.", Toast.LENGTH_LONG).show()
                                                                return@launch
                                                            }
                                                            try {
                                                                supabase.from("users").update({ set("role", "BANNED") }) { filter { eq("id", record.id) } }

                                                                // Move user to the Banned list visually
                                                                newlyBannedEmails = newlyBannedEmails + record.email
                                                                newlyUnbannedEmails = newlyUnbannedEmails - record.email

                                                                Toast.makeText(context, "User successfully blocked.", Toast.LENGTH_SHORT).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Error blocking user: ${e.message}", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                                    modifier = Modifier.fillMaxWidth().height(35.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text("BLOCK USER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            // IF WE ARE IN THE BANNED TAB, SHOW UNBLOCK BUTTON
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        if (record.id.isBlank()) {
                                                            Toast.makeText(context, "Cannot unblock: Missing User ID.", Toast.LENGTH_LONG).show()
                                                            return@launch
                                                        }
                                                        try {
                                                            supabase.from("users").update({ set("role", "CITIZEN") }) { filter { eq("id", record.id) } }

                                                            // Move user back to the Active list visually
                                                            newlyUnbannedEmails = newlyUnbannedEmails + record.email
                                                            newlyBannedEmails = newlyBannedEmails - record.email

                                                            Toast.makeText(context, "User unblocked successfully.", Toast.LENGTH_SHORT).show()
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Error unblocking user: ${e.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), // Green color for unblocking
                                                modifier = Modifier.fillMaxWidth().height(35.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("UNBLOCK USER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuditDialog = false }) { Text("CLOSE", fontWeight = FontWeight.Bold) }
            }
        )
    }

    if (showProfileDialog) {
        var isEditing by remember { mutableStateOf(false) }
        var editName by remember { mutableStateOf(currentUserProfile?.name ?: "") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showProfileDialog = false },
            title = { Text("Moderator Profile", fontWeight = FontWeight.ExtraBold, color = Color(0xFF154360)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Account Details", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Email:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(currentUserEmail, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSaving
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Text("Name:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(currentUserProfile?.name ?: "Unknown User", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text("Contact Info:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(currentUserProfile?.contact_info ?: "Not registered", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("System Role:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(currentUserProfile?.role ?: "MODERATOR", color = Color(0xFFF57C00), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Registration Date:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(currentUserProfile?.created_at?.take(10) ?: "Unknown Date", fontSize = 14.sp)
                }
            },
            confirmButton = {
                if (isEditing) {
                    Button(
                        onClick = {
                            if (editName.isNotBlank()) {
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        supabase.from("users").update({
                                            set("name", editName)
                                        }) {
                                            filter { eq("id", currentUserId) }
                                        }
                                        currentUserProfile = currentUserProfile?.copy(name = editName)
                                        isEditing = false
                                        Toast.makeText(context, "Profile Name Updated!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Update Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Name cannot be empty.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        enabled = !isSaving
                    ) {
                        Text(if (isSaving) "SAVING..." else "SAVE", fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { showProfileDialog = false }) {
                        Text("CLOSE", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (isEditing) {
                    TextButton(onClick = { isEditing = false }, enabled = !isSaving) {
                        Text("CANCEL", color = Color.Gray)
                    }
                } else {
                    TextButton(onClick = {
                        isEditing = true
                        editName = currentUserProfile?.name ?: ""
                    }) {
                        Text("EDIT NAME", color = Color(0xFF154360), fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }
}