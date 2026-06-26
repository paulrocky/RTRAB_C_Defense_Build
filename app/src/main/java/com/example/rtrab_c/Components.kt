package com.example.rtrab_c

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

// --- The ONLY Serializable import allowed ---
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


// ==========================================
// SUPABASE DATA MODEL
// ==========================================

@Serializable
data class HazardReport(
    val id: String? = null,
    val type: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val timestamp: Long = 0L,
    val reporterEmail: String = "",
    val status: String = "",
    val upvotes: Int = 0,
    val votedUsers: List<String> = emptyList(),
    val imageUrl: String? = null
)

@Serializable
data class UserRole(
    val id: String = "",
    val email: String = "",
    val role: String = "CITIZEN"
)

// ==========================================
// REUSABLE UI COMPONENTS
// ==========================================
fun createColoredMarker(context: Context, type: String): Drawable {
    val size = 60
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    paint.color = when (type) {
        "Flood" -> android.graphics.Color.BLUE
        "Fire" -> android.graphics.Color.RED
        "Landslide" -> android.graphics.Color.parseColor("#FF9800")
        "Accident" -> android.graphics.Color.parseColor("#FFEB3B")
        "other" -> android.graphics.Color.parseColor("#2596be")
        else -> android.graphics.Color.DKGRAY
    }
    canvas.drawCircle(30f, 30f, 30f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 4f
    canvas.drawCircle(30f, 30f, 28f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

@Composable
fun AlertItem(
    title: String,
    address: String,
    status: String?,
    hazardType: String = "",
    upvotes: Int? = 0,
    hasVoted: Boolean = false,
    imageUrl: String? = null,
    onUpvote: () -> Unit = {}
) {
    val safeStatus = status ?: "PENDING"
    val displayStatus = if (safeStatus == "VERIFIED") "VERIFIED (Personnel Dispatched)" else safeStatus
    val statusColor = when (safeStatus) { "RESOLVED" -> Color(0xFF4CAF50); "VERIFIED" -> Color(0xFFF57C00); "REJECTED" -> Color(0xFF9E9E9E); else -> Color(0xFFE53935) }
    val hazardIcon = when (hazardType) { "Flood" -> "🌊"; "Fire" -> "🔥"; "Landslide" -> "🪨"; "Accident" -> "💥"; else -> "⚠️" }

    val safeUpvotes = upvotes ?: 0
    var showFullImage by remember { mutableStateOf(false) }

    Column(Modifier.padding(bottom = 12.dp, top = 8.dp, start = 8.dp, end = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = imageUrl,
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
                Text("$hazardIcon $title", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)

                if (address.isNotEmpty()) {
                    Text("📍 $address", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp))
                }

                Text("Status: $displayStatus", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = statusColor)
            }

            if (safeStatus == "PENDING" || safeStatus == "VERIFIED") {
                Button(
                    onClick = onUpvote,
                    enabled = !hasVoted,
                    colors = ButtonDefaults.buttonColors(containerColor = if (hasVoted) Color(0xFFE8F5E9) else Color(0xFFE0E0E0), disabledContainerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(35.dp).padding(start = 8.dp)
                ) {
                    Text(if (hasVoted) "✅ Verified ($safeUpvotes)" else "👍 Verify ($safeUpvotes)", color = if (hasVoted) Color(0xFF2E7D32) else Color.DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showFullImage && !imageUrl.isNullOrEmpty()) {
        Dialog(onDismissRequest = { showFullImage = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFullImage = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Full Size Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
fun RowScope.NavButton(
    text: String,
    bgColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            disabledContainerColor = Color.LightGray
        ),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(45.dp)
    ) {
        Text(text, color = if (enabled) Color.White else Color.DarkGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun DashboardStatBox(label: String, count: Int, bgColor: Color, textColor: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(bgColor, RoundedCornerShape(4.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = textColor)
            Text("$count", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColor)
        }
    }
}

@Composable
fun rememberNetworkStatus(): State<Boolean> {
    val context = LocalContext.current
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val initialConnection = connectivityManager.activeNetwork?.let { network ->
        connectivityManager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } ?: false

    val isConnected = remember { mutableStateOf(initialConnection) }

    DisposableEffect(connectivityManager) {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isConnected.value = true }
            override fun onLost(network: Network) { isConnected.value = false }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        onDispose { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }
    return isConnected
}