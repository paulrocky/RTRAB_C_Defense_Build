package com.example.rtrab_c

// --- Supabase Imports ---
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.query.filter.*

// --- Android & System Imports ---
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

// --- Serialization Import (CRITICAL FOR DATABASE PARSING) ---
import kotlinx.serialization.Serializable

// --- OsmDroid Imports ---
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// ==========================================
// DATA MODEL FOR PROFILE (Matches your ERD)
// ==========================================
@Serializable
data class FullUserProfile(
    val id: String,
    val name: String? = null,
    val role: String? = null,
    val contact_info: String? = null,
    val created_at: String? = null
)

// ==========================================
// SCREEN: PUBLIC RISK MAP
// ==========================================
@Composable
fun PublicRiskMapScreen(
    supabase: SupabaseClient,
    onNavigateToReport: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToHotlines: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeAlerts by remember { mutableStateOf(listOf<HazardReport>()) }
    var isModerator by remember { mutableStateOf(false) }
    var mapStyle by remember { mutableStateOf("STREETS") }
    var mapReference by remember { mutableStateOf<MapView?>(null) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var dangerWarning by remember { mutableStateOf("") }
    var hasLocationPermission by remember { mutableStateOf(false) }

    // --- MENU & PROFILE STATES ---
    var showMenu by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var currentUserProfile by remember { mutableStateOf<FullUserProfile?>(null) }

    val currentUserId = supabase.auth.currentUserOrNull()?.id ?: ""
    val currentUserEmail = supabase.auth.currentUserOrNull()?.email ?: "No Email"

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        hasLocationPermission = perms[android.Manifest.permission.ACCESS_FINE_LOCATION] == true || perms[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(context, "Please turn on GPS.", Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))

        // Fetch the full ERD user profile
        if (currentUserId.isNotEmpty()) {
            try {
                val userData = supabase.from("users")
                    .select { filter { eq("id", currentUserId) } }
                    .decodeSingleOrNull<FullUserProfile>()

                // ==========================================
                // SPAMMER BLOCK CHECK (ON LOAD)
                // ==========================================
                if (userData?.role == "BANNED") {
                    Toast.makeText(context, "Your account has been blocked by LGU Administrators due to spam.", Toast.LENGTH_LONG).show()
                    onLogout()
                    return@LaunchedEffect
                }

                currentUserProfile = userData
                isModerator = (userData?.role == "MODERATOR")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            activeAlerts = supabase.from("reports")
                .select()
                .decodeList<HazardReport>()
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) { }
    }

    LaunchedEffect(hasLocationPermission, mapReference) {
        if (hasLocationPermission && mapReference != null && locationOverlay == null) {
            val provider = GpsMyLocationProvider(context)
            val overlay = MyLocationNewOverlay(provider, mapReference)

            overlay.enableMyLocation()
            overlay.enableFollowLocation()

            overlay.runOnFirstFix {
                mapReference?.post {
                    mapReference?.controller?.animateTo(overlay.myLocation)
                    mapReference?.controller?.setZoom(18.0)
                }
            }

            locationOverlay = overlay
            mapReference?.overlays?.add(overlay)
            mapReference?.invalidate()
        }
    }

    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(activeAlerts) {
        while (true) {
            currentTime = SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            val myLoc = locationOverlay?.myLocation
            if (myLoc != null) {
                val verifiedHazards = activeAlerts.filter { it.status == "VERIFIED" }
                val nearbyHazard = verifiedHazards.find { hazard ->
                    myLoc.distanceToAsDouble(GeoPoint(hazard.latitude, hazard.longitude)) < 500.0
                }
                dangerWarning = if (nearbyHazard != null) "PROXIMITY ALERT: You are near a verified ${nearbyHazard.type.uppercase()} zone!" else ""
            }
            delay(3000)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { mapReference?.onResume(); locationOverlay?.enableMyLocation() }
                Lifecycle.Event.ON_PAUSE -> { mapReference?.onPause(); locationOverlay?.disableMyLocation() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(Color.White).verticalScroll(rememberScrollState()).padding(16.dp)) {

        // --- HEADER WITH 3-DOT MENU ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(id = R.drawable.rtrab_logo), contentDescription = "Logo", modifier = Modifier.size(70.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("RTRAB-C", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text("Baguio Real-Time Risk\nAssessment", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp)
                }
            }

            // 3-DOTS MENU REPLACING LOGOUT
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

        // --- MAP CONTROLS ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(currentTime, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.background(Color(0xFF4CAF50), shape = RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("LIVE MAP", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    Box(modifier = Modifier
                        .background(Color(0xFFE0E0E0), shape = RoundedCornerShape(4.dp))
                        .clickable {
                            locationOverlay?.enableFollowLocation()
                            val currentLoc = locationOverlay?.myLocation
                            if (currentLoc != null) {
                                mapReference?.controller?.animateTo(currentLoc)
                                mapReference?.controller?.setZoom(18.0)
                            } else {
                                Toast.makeText(context, "Acquiring GPS signal... Please wait.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("📍 Locate Me", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(modifier = Modifier
                        .background(Color(0xFFE53935), shape = RoundedCornerShape(4.dp))
                        .clickable { onNavigateToHotlines() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(" Hotlines", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (dangerWarning.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().background(Color(0xFFD32F2F), RoundedCornerShape(8.dp)).padding(12.dp)) {
                Text("⚠️ $dangerWarning", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))) {
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
                        clipToOutline = true; setMultiTouchControls(true); setBuiltInZoomControls(true)
                        controller.setZoom(16.0); controller.setCenter(GeoPoint(16.4164, 120.5930))
                        mapReference = this
                    }
                },
                update = { mapView ->
                    mapView.setTileSource(if (mapStyle == "STREETS") XYTileSource("CartoPositron", 1, 20, 256, ".png", arrayOf("https://cartodb-basemaps-a.global.ssl.fastly.net/light_all/")) else TileSourceFactory.OpenTopo)

                    mapView.overlays.clear()
                    locationOverlay?.let { mapView.overlays.add(it) }

                    activeAlerts.forEach { report ->
                        if (report.status == "PENDING" || report.status == "VERIFIED") {
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(report.latitude, report.longitude)
                            marker.title = report.description
                            marker.icon = createColoredMarker(context, report.type)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            mapView.overlays.add(marker)
                        }
                    }
                    mapView.invalidate()
                }
            )

            Column(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Box(Modifier.background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)).clickable { mapStyle = if (mapStyle == "STREETS") "TERRAIN" else "STREETS" }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(if (mapStyle == "STREETS") "🗺️ Terrain" else "🛣️ Streets", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        Text("Active Alerts (City-Wide):", color = Color(0xFF00796B), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(16.dp))

        val publicVisibleAlerts = activeAlerts.filter { it.status == "PENDING" || it.status == "VERIFIED" }

        if (publicVisibleAlerts.isEmpty()) {
            Text("No active alerts reported.", color = Color.Gray, fontSize = 14.sp)
        } else {
            publicVisibleAlerts.forEach { report ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { mapReference?.controller?.animateTo(GeoPoint(report.latitude, report.longitude)); mapReference?.controller?.setZoom(18.0) }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                    Column {
                        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
                        val formattedTime = sdf.format(java.util.Date(report.timestamp))

                        Text(
                            text = formattedTime,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                        )

                        val hasAlreadyVoted = report.votedUsers?.contains(currentUserId) == true

                        AlertItem(
                            title = report.description,
                            address = report.address,
                            status = report.status,
                            hazardType = report.type,
                            upvotes = report.upvotes ?: 0,
                            hasVoted = hasAlreadyVoted,
                            imageUrl = report.imageUrl,
                            onUpvote = {
                                if (!hasAlreadyVoted) {
                                    coroutineScope.launch {
                                        try {
                                            val safeVotersList = report.votedUsers ?: emptyList()
                                            val updatedVoters = safeVotersList + currentUserId
                                            val updatedCount = (report.upvotes ?: 0) + 1

                                            supabase.from("reports").update({
                                                set("upvotes", updatedCount)
                                                set("votedUsers", updatedVoters)
                                            }) { filter { eq("id", report.id ?: "") } }

                                            activeAlerts = activeAlerts.map {
                                                if (it.id == report.id) it.copy(upvotes = updatedCount, votedUsers = updatedVoters) else it
                                            }
                                        } catch (e: Exception) { }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // ==========================================
            // SPAMMER BLOCK CHECK (ON REPORT CLICK)
            // ==========================================
            NavButton("REPORT", bgColor = Color(0xFFE53935), onClick = {
                if (currentUserProfile?.role == "BANNED") {
                    Toast.makeText(context, "Your account is blocked. You cannot submit reports.", Toast.LENGTH_LONG).show()
                    onLogout()
                } else {
                    onNavigateToReport()
                }
            })

            if (isModerator) NavButton("ALERTS", bgColor = Color(0xFFF57C00), onClick = onNavigateToAlerts)
        }
    }

    // ==========================================
    // ERD PROFILE DIALOG WITH EDIT NAME FEATURE
    // ==========================================
    if (showProfileDialog) {
        var isEditing by remember { mutableStateOf(false) }
        var editName by remember { mutableStateOf(currentUserProfile?.name ?: "") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showProfileDialog = false },
            title = { Text(if(isModerator) "Moderator Profile" else "Citizen Profile", fontWeight = FontWeight.ExtraBold, color = Color(0xFF154360)) },
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
                    Text(currentUserProfile?.role ?: "CITIZEN", color = if (isModerator) Color(0xFFF57C00) else Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
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
                                        // Update the Name field in Supabase
                                        supabase.from("users").update({
                                            set("name", editName)
                                        }) {
                                            filter { eq("id", currentUserId) }
                                        }
                                        // Update Local UI instantly
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