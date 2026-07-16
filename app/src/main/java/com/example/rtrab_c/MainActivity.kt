package com.example.rtrab_c

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope


// --- Supabase Imports ---
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import io.github.jan.supabase.auth.auth


class MainActivity : ComponentActivity() {

    // 1. SUPABASE CLIENT
    val supabase = createSupabaseClient(
        supabaseUrl = "https://ppapyuatgjusnsptqvrk.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBwYXB5dWF0Z2p1c25zcHRxdnJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4NDM1MzMsImV4cCI6MjA5NTQxOTUzM30.gCbVVP_gkXNnLOlMmg14wZPoItJvVjiCET65ppd4ANM"
    ) {
        install(Postgrest); install(Auth); install(Realtime); install(Storage)
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true })
    }

    // --- HARDWARE NETWORK CHECK ---
    private fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        setContent {
            // ALWAYS start on the Login screen. No automatic skipping!
            var currentScreen by remember { mutableStateOf("LOGIN") }

            // ==========================================
            // THE BACKGROUND SYNC WATCHDOG
            // ==========================================
            LaunchedEffect(Unit) {
                launch(Dispatchers.IO) {
                    while(true) {
                        // If internet is connected, look for hidden offline reports
                        if (isOnline(this@MainActivity)) {
                            try {
                                // Grab the helper from SubmitReportScreen.kt
                                val dbHelper = OfflineDatabaseHelper(this@MainActivity)
                                // Push them to Supabase!
                                dbHelper.syncPendingReportsToCloud(supabase)
                            } catch (e: Exception) {
                                // Silent fail if Supabase is temporarily unreachable
                            }
                        }
                        // Check again every 5 seconds
                        delay(5000)
                    }
                }
            }

            // NAVIGATION HUB
            when (currentScreen) {
                "LOGIN" -> LoginScreen(
                    supabase = supabase,
                    onLoginSuccess = { currentScreen = it },
                    onNavigateToSignUp = { currentScreen = "SIGNUP" }
                )
                "SIGNUP" -> SignUpScreen(
                    supabase = supabase,
                    onSignUpSuccess = { currentScreen = it },
                    onNavigateToLogin = { currentScreen = "LOGIN" }
                )
                "HOME" -> PublicRiskMapScreen(
                    supabase = supabase,
                    onNavigateToReport = { currentScreen = "REPORT" },
                    onNavigateToAlerts = { currentScreen = "MODERATOR_DASHBOARD" },
                    onNavigateToHotlines = { currentScreen = "HOTLINES" }, // FIXED: Passed the new navigation parameter
                    onLogout = {
                        lifecycleScope.launch { try { supabase.auth.signOut() } catch (e: Exception) { } }
                        currentScreen = "LOGIN"
                    }
                )
                "REPORT" -> SubmitReportScreen(
                    supabase = supabase,
                    onNavigateHome = { currentScreen = "HOME" }
                )
                "MODERATOR_DASHBOARD" -> ModeratorDashboardScreen(
                    supabase = supabase,
                    onNavigateHome = { currentScreen = "HOME" },
                    onLogout = {
                        lifecycleScope.launch { try { supabase.auth.signOut() } catch (e: Exception) { } }
                        currentScreen = "LOGIN"
                    }
                )
                "HOTLINES" -> EmergencyHotlinesScreen(
                    onNavigateBack = { currentScreen = "HOME" } // ADDED: Direct routing back to map screen
                )
            }
        }
    }
}