package com.example.rtrab_c

// --- Supabase Imports ---
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage

// --- Android & System Imports ---
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.location.LocationManager
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

// --- OsmDroid Imports ---
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// ==========================================
// SCREEN: SUBMIT REPORT
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitReportScreen(
    supabase: SupabaseClient,
    onNavigateHome: () -> Unit
) {
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var mapViewReference: MapView? by remember { mutableStateOf(null) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> capturedBitmap = bitmap }

    var searchQuery by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var mapStyle by remember { mutableStateOf("STREETS") }
    var hasLocationPermission by remember { mutableStateOf(false) }

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
    }

    LaunchedEffect(hasLocationPermission, mapViewReference) {
        if (hasLocationPermission && mapViewReference != null && locationOverlay == null) {
            val provider = GpsMyLocationProvider(context)
            val overlay = MyLocationNewOverlay(provider, mapViewReference)
            overlay.enableMyLocation()
            overlay.enableFollowLocation()

            overlay.runOnFirstFix {
                mapViewReference?.post {
                    mapViewReference?.controller?.animateTo(overlay.myLocation)
                    mapViewReference?.controller?.setZoom(18.0)
                }
            }

            locationOverlay = overlay
            mapViewReference?.overlays?.add(overlay)
            mapViewReference?.invalidate()
        }
    }

    val baguioLocations = listOf("Irisan", "Burnham Park", "Session Road", "Camp John Hay", "Mines View Park", "Loakan", "Pacdal", "Trancoville", "Aurora Hill", "Baguio City Market", "SM City Baguio", "Wright Park", "Botanical Garden", "Kennon Road", "Marcos Highway", "Asin Road", "Bakakeng", "San Luis Village", "Baguio General Hospital", "Slaughter House")
    val filteredLocations = baguioLocations.filter { it.contains(searchQuery, ignoreCase = true) && searchQuery.isNotEmpty() }.take(4)

    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            delay(1000)
        }
    }

    val isOnline by rememberNetworkStatus()

    // --- UPDATED HAZARD STATE VARIABLES ---
    var selectedCategory by remember { mutableStateOf("Landslide") }
    var otherCategoryText by remember { mutableStateOf("") }

    val executeSearch = { query: String ->
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val results = android.location.Geocoder(context, java.util.Locale.getDefault()).getFromLocationName("$query, Baguio City, Philippines", 1)
                if (!results.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) { mapViewReference?.controller?.animateTo(GeoPoint(results[0].latitude, results[0].longitude)); mapViewReference?.controller?.setZoom(18.0) }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Location not found.", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "Search Error.", Toast.LENGTH_SHORT).show() } }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { mapViewReference?.onResume(); locationOverlay?.enableMyLocation() }
                Lifecycle.Event.ON_PAUSE -> { mapViewReference?.onPause(); locationOverlay?.disableMyLocation() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(id = R.drawable.rtrab_logo), contentDescription = "Logo", modifier = Modifier.size(70.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("RTRAB-C", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                Text("Baguio Real-Time Risk\nAssessment", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 18.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateHome, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF154360)), shape = RoundedCornerShape(4.dp), modifier = Modifier.height(40.dp)) { Text("Home", color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(8.dp))
        Text(if (currentTime.isEmpty()) "10:32 AM" else currentTime, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(Modifier.background(if (isOnline) Color(0xFF3B5998) else Color(0xFF757575), RoundedCornerShape(4.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) { Text(if (isOnline) "ONLINE" else "OFFLINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        Spacer(modifier = Modifier.height(24.dp))

        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(searchQuery, { searchQuery = it; showSuggestions = true }, placeholder = { Text("Search Baguio locations...", color = Color.Gray) }, modifier = Modifier.weight(1f).height(50.dp), singleLine = true, shape = RoundedCornerShape(4.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { showSuggestions = false; if (searchQuery.isNotEmpty()) executeSearch(searchQuery) }, modifier = Modifier.height(50.dp), shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF154360))) { Text("🔍", fontSize = 18.sp) }
            }
            if (showSuggestions && filteredLocations.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(top = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0)), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                    Column {
                        filteredLocations.forEach { loc ->
                            Text("📍 $loc", Modifier.fillMaxWidth().clickable { searchQuery = loc; showSuggestions = false; executeSearch(loc) }.padding(16.dp), color = Color.DarkGray, fontWeight = FontWeight.Bold)
                            HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(300.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
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
                        controller.setZoom(17.0); controller.setCenter(GeoPoint(16.4164, 120.5930))
                        mapViewReference = this
                    }
                },
                update = { mv ->
                    mv.setTileSource(if (mapStyle == "STREETS") XYTileSource("CartoPositron", 1, 20, 256, ".png", arrayOf("https://cartodb-basemaps-a.global.ssl.fastly.net/light_all/")) else TileSourceFactory.OpenTopo)
                }
            )
            Text("📍", fontSize = 42.sp, modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp))
            Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Box(Modifier.background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)).clickable { mapStyle = if (mapStyle == "STREETS") "TERRAIN" else "STREETS" }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(if (mapStyle == "STREETS") "🗺️ Terrain" else "🛣️ Streets", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (hasLocationPermission) {
                    Box(Modifier.align(Alignment.End).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)).clickable { locationOverlay?.enableFollowLocation(); locationOverlay?.myLocation?.let { mapViewReference?.controller?.animateTo(it); mapViewReference?.controller?.setZoom(18.0) } }.padding(8.dp)) {
                        Text("🎯", fontSize = 16.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        Text("Report Hazard", color = Color(0xFF00695C), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // --- NEW HAZARD BUTTON GRID ---
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Flood & Fire
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { selectedCategory = "Flood" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedCategory == "Flood") Color(0xFF1E88E5) else Color(0xFFE0E0E0)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Flood", color = if (selectedCategory == "Flood") Color.White else Color.DarkGray, fontWeight = FontWeight.Bold) }

                Button(
                    onClick = { selectedCategory = "Fire" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedCategory == "Fire") Color(0xFFE53935) else Color(0xFFE0E0E0)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Fire", color = if (selectedCategory == "Fire") Color.White else Color.DarkGray, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Landslide & Accident
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { selectedCategory = "Landslide" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedCategory == "Landslide") Color(0xFF8D6E63) else Color(0xFFE0E0E0)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Landslide", color = if (selectedCategory == "Landslide") Color.White else Color.DarkGray, fontWeight = FontWeight.Bold) }

                Button(
                    onClick = { selectedCategory = "Accident" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedCategory == "Accident") Color(0xFFFB8C00) else Color(0xFFE0E0E0)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Accident", color = if (selectedCategory == "Accident") Color.White else Color.DarkGray, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Other
            Button(
                onClick = { selectedCategory = "Other" },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedCategory == "Other") Color(0xFF546E7A) else Color(0xFFE0E0E0)),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Other", color = if (selectedCategory == "Other") Color.White else Color.DarkGray, fontWeight = FontWeight.Bold) }

            // Hidden Text Box (Only shows if "Other" is tapped)
            if (selectedCategory == "Other") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = otherCategoryText,
                    onValueChange = { otherCategoryText = it },
                    placeholder = { Text("Specify the hazard (e.g., Live Wire)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                )
            }
        }
        // --- END HAZARD BUTTON GRID ---

        Spacer(modifier = Modifier.height(40.dp))
        Text("Photo Evidence", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(80.dp).background(Color.White).border(2.dp, Color.Black, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                if (capturedBitmap != null) Image(capturedBitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Text("📷", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(30.dp))
            Button(onClick = { cameraLauncher.launch(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)), shape = RoundedCornerShape(4.dp)) { Text("Take Photo", color = Color.White, fontWeight = FontWeight.Bold) }
        }

        Spacer(modifier = Modifier.height(40.dp))
        Text("Description Details", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(description, { description = it }, placeholder = { Text("Type details here", color = Color.Gray) }, modifier = Modifier.fillMaxWidth().height(120.dp), colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFE0E0E0), unfocusedContainerColor = Color(0xFFE0E0E0)))

        Spacer(modifier = Modifier.height(40.dp))

        val buttonText = if (isSubmitting) "SAVING..." else if (isOnline) "Upload Report" else "Save Offline Report"

        Button(
            onClick = {
                // If the user wants spam protection, force them to take a photo!
                if (description.isEmpty()) {
                    Toast.makeText(context, "Please enter a description.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (capturedBitmap == null) {
                    Toast.makeText(context, "Photo evidence is required to prevent spam.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                isSubmitting = true
                val currentLat = (mapViewReference?.mapCenter as? GeoPoint)?.latitude ?: 16.4164
                val currentLon = (mapViewReference?.mapCenter as? GeoPoint)?.longitude ?: 120.5930

                // --- NEW: GRAB BOTH EMAIL AND ID ---
                val email = supabase.auth.currentUserOrNull()?.email ?: "Unknown"
                val currentUserId = supabase.auth.currentUserOrNull()?.id ?: ""

                // Determine final category string to push to DB
                val finalCategoryToSave = if (selectedCategory == "Other" && otherCategoryText.isNotBlank()) {
                    otherCategoryText
                } else {
                    selectedCategory
                }

                coroutineScope.launch(Dispatchers.IO) {
                    var fetchedAddress = "GPS: $currentLat, $currentLon"

                    if (isOnline) {
                        try {
                            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                            val addresses = geocoder.getFromLocation(currentLat, currentLon, 1)
                            if (!addresses.isNullOrEmpty()) {
                                fetchedAddress = addresses[0].getAddressLine(0) ?: fetchedAddress
                            }
                        } catch (e: Exception) { }

                        var finalImageUrl: String? = null
                        if (capturedBitmap != null) {
                            try {
                                val stream = ByteArrayOutputStream()
                                capturedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                                val byteArray = stream.toByteArray()
                                val fileName = "hazard_${System.currentTimeMillis()}.jpg"

                                supabase.storage.from("hazard-images").upload(fileName, byteArray)
                                finalImageUrl = supabase.storage.from("hazard-images").publicUrl(fileName)
                            } catch (e: Exception) { }
                        }

                        val newReport = HazardReport(
                            type = finalCategoryToSave,
                            description = description,
                            latitude = currentLat,
                            longitude = currentLon,
                            address = fetchedAddress,
                            timestamp = System.currentTimeMillis(),
                            reporterEmail = email,
                            status = "PENDING",
                            upvotes = 0,
                            votedUsers = emptyList(),
                            imageUrl = finalImageUrl,
                            reporterId = currentUserId // --- ATTACH ID TO ONLINE REPORT ---
                        )

                        try {
                            supabase.from("reports").insert(newReport)
                            withContext(Dispatchers.Main) {
                                isSubmitting = false
                                Toast.makeText(context, "Uploaded to City Servers!", Toast.LENGTH_SHORT).show()
                                description = ""
                                otherCategoryText = ""
                                capturedBitmap = null
                                onNavigateHome()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isSubmitting = false
                                Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        // ==========================================
                        // OFFLINE MODE: FILE CACHE SYSTEM
                        // ==========================================
                        var savedImagePath: String? = null
                        if (capturedBitmap != null) {
                            try {
                                // 1. Save the high-res photo to the Android internal cache safely
                                val fileName = "offline_hazard_${System.currentTimeMillis()}.jpg"
                                val file = File(context.filesDir, fileName)
                                val outStream = FileOutputStream(file)
                                // Compress to 70% to save offline storage space
                                capturedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 70, outStream)
                                outStream.flush()
                                outStream.close()

                                // 2. Record where we hid the file
                                savedImagePath = file.absolutePath
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        withContext(Dispatchers.Main) {
                            try {
                                val dbHelper = OfflineDatabaseHelper(context)
                                val db = dbHelper.writableDatabase
                                val values = ContentValues().apply {
                                    put("type", finalCategoryToSave)
                                    put("description", description)
                                    put("latitude", currentLat)
                                    put("longitude", currentLon)
                                    put("address", fetchedAddress)
                                    put("timestamp", System.currentTimeMillis())
                                    put("reporterEmail", email)
                                    put("reporterId", currentUserId) // --- ATTACH ID TO OFFLINE SQLITE ---
                                    put("imagePath", savedImagePath)
                                }
                                db.insert("offline_reports", null, values)
                                db.close()

                                isSubmitting = false
                                Toast.makeText(context, "Saved Image & Data Offline!", Toast.LENGTH_LONG).show()
                                description = ""
                                otherCategoryText = ""
                                capturedBitmap = null
                                onNavigateHome()
                            } catch (e: Exception) {
                                isSubmitting = false
                                Toast.makeText(context, "Failed to save offline: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.5f).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
            shape = RoundedCornerShape(4.dp),
            enabled = !isSubmitting
        ) {
            Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

// ==========================================
// OFFLINE SQLITE DATABASE & SYNC ENGINE
// ==========================================
// Upgraded to Version 3 to support reporterId column!
class OfflineDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "RTRABC_Offline.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE offline_reports (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "type TEXT, description TEXT, latitude REAL, longitude REAL, " +
                    "address TEXT, timestamp INTEGER, reporterEmail TEXT, " +
                    "reporterId TEXT, " + // --- NEW: Holds the Auth UUID ---
                    "imagePath TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS offline_reports")
        onCreate(db)
    }

    suspend fun syncPendingReportsToCloud(supabase: SupabaseClient) {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM offline_reports", null)

        // We will process uploads one by one
        if (cursor.moveToFirst()) {
            do {
                val localId = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                val desc = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                val lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"))
                val lon = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"))
                val address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                val email = cursor.getString(cursor.getColumnIndexOrThrow("reporterEmail"))
                val reporterId = cursor.getString(cursor.getColumnIndexOrThrow("reporterId")) // --- EXTRACT ID FOR SYNC ---

                // Get the saved path
                val imagePath = cursor.getString(cursor.getColumnIndexOrThrow("imagePath"))
                var finalImageUrl: String? = null

                try {
                    // 1. IF IT HAS AN OFFLINE IMAGE, UPLOAD IT FIRST
                    if (!imagePath.isNullOrEmpty()) {
                        val imageFile = File(imagePath)
                        if (imageFile.exists()) {
                            val byteArray = imageFile.readBytes()
                            val fileName = "offline_sync_${System.currentTimeMillis()}.jpg"

                            // Upload the file bytes to Supabase
                            supabase.storage.from("hazard-images").upload(fileName, byteArray)
                            finalImageUrl = supabase.storage.from("hazard-images").publicUrl(fileName)

                            // Clean up the phone storage so we don't waste user's space
                            imageFile.delete()
                        }
                    }

                    // 2. BUILD THE REPORT OBJECT
                    val report = HazardReport(
                        type = type,
                        description = desc,
                        latitude = lat,
                        longitude = lon,
                        address = address,
                        timestamp = timestamp,
                        reporterEmail = email,
                        status = "PENDING",
                        upvotes = 0,
                        votedUsers = emptyList(),
                        imageUrl = finalImageUrl,
                        reporterId = reporterId // --- ATTACH ID TO SYNCED REPORT ---
                    )

                    // 3. PUSH TO DATABASE
                    supabase.from("reports").insert(report)

                    // 4. DELETE THE SQLITE ROW SO IT DOESN'T DUPLICATE
                    val writeDb = this.writableDatabase
                    writeDb.delete("offline_reports", "id=?", arrayOf(localId.toString()))

                } catch (e: Exception) {
                    // If the upload crashes (e.g. internet died mid-upload), it throws an error.
                    // We catch it so it DOES NOT delete the local SQLite row. It will just try again later!
                    e.printStackTrace()
                }

            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
    }
}