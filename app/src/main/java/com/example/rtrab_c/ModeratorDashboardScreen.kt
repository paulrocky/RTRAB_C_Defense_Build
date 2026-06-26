package com.example.rtrab_c

// --- Supabase Imports ---
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.*

// --- Android & System Imports ---
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

// ==========================================
// SCREEN: MODERATOR DASHBOARD
// ==========================================
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
    var showAuditDialog by remember { mutableStateOf(false) } // <-- ADDED: Trigger for the new audit popup
    var mapReference by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        try {
            allReports = supabase.from("reports")
                .select()
                .decodeList<HazardReport>()
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load reports: ${e.message}", Toast.LENGTH_LONG).show()
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

    val totalReports = allReports.size
    val pendingReports = allReports.count { it.status == "PENDING" }
    val verifiedReports = allReports.count { it.status == "VERIFIED" }
    val resolvedReports = allReports.count { it.status == "RESOLVED" }
    val rejectedReports = allReports.count { it.status == "REJECTED" }

    LazyColumn(Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .background(Color.White)
        .padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.rtrab_logo), contentDescription = "Logo", modifier = Modifier.size(70.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("RTRAB-C", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("Baguio Real-Time Risk\nAssessment", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // <-- ADDED: The new split buttons for Analytics and User Audit
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showAnalyticsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(45.dp)
                ) {
                    // Separated the Emoji and Text so they can have different sizes
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📊", fontSize = 18.sp) // Bigger shape/emoji
                        Spacer(modifier = Modifier.width(6.dp)) // Small gap between them
                        Text("ANALYTICS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = { showAuditDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)), // Matching your screenshot color
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(45.dp)
                ) {
                    // Separated the Emoji and Text so they can have different sizes
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️", fontSize = 18.sp) // Bigger shape/emoji
                        Spacer(modifier = Modifier.width(6.dp)) // Small gap between them
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
                Text("Logout", color = Color.Red, fontSize = 12.sp, modifier = Modifier.clickable { onLogout() }.padding(8.dp))
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
                        object : MapView(mapContext) {
                            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                                when (ev.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> parent.requestDisallowInterceptTouchEvent(true)
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
                                }
                                return super.dispatchTouchEvent(ev)
                            }
                        }.apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(14.0)
                            controller.setCenter(GeoPoint(16.4164, 120.5930)) // Centered on Baguio
                            mapReference = this
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()
                        // Tactical Map only shows Active/Pending issues to keep it clean
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

            Text("CITY REPORT DATABASE", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (allReports.isEmpty()) {
            item { Text("No reports in the database.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp)) }
        } else {
            items(allReports) { report ->

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
                                    Text("📍 ${report.address}", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Reported by: ${report.reporterEmail}", color = Color.Gray, fontSize = 12.sp)
                                Text("Status: ${report.status}", color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                                Spacer(modifier = Modifier.height(12.dp))

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
                                            modifier = Modifier.weight(1f)
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
                                            modifier = Modifier.weight(1f)
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
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("MARK AS RESOLVED (FIXED)", fontWeight = FontWeight.Bold) }
                                } else {
                                    Box(Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                                        Text("🔒 This report has been closed and archived.", color = Color.Gray, fontSize = 11.sp)
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

    // <-- ADDED: The new Citizen Trust & Audit Log Pop-up
    if (showAuditDialog) {
        data class AuditRecord(val email: String, val total: Int, val spam: Int, val firstDate: Long)

        val userStats = allReports.groupBy { it.reporterEmail }.map { (email, reports) ->
            val total = reports.size
            val spamCount = reports.count { it.status == "REJECTED" }
            val firstDate = reports.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()

            AuditRecord(email ?: "Unknown User", total, spamCount, firstDate)
        }.sortedByDescending { it.spam }

        AlertDialog(
            onDismissRequest = { showAuditDialog = false },
            title = { Text("Citizen Trust & Audit Log", fontWeight = FontWeight.ExtraBold, color = Color(0xFF154360)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Identify users submitting multiple fake or rejected reports.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (userStats.isEmpty()) {
                        Text("No user data available.")
                    } else {
                        userStats.forEach { record ->
                            Card(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = if (record.spam > 2) Color(0xFFFFEBEE) else Color(0xFFF5F5F5))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
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
}