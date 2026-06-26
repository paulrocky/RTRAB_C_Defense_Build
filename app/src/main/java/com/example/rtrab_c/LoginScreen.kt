package com.example.rtrab_c

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import java.security.MessageDigest
import io.github.jan.supabase.auth.auth


// --- CRYPTOGRAPHIC HASHING (Secures the password) ---
fun hashPassword(password: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    supabase: SupabaseClient,
    onLoginSuccess: (String) -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = context.getSharedPreferences("RTRABC_Prefs", Context.MODE_PRIVATE)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // --- HARDWARE NETWORK CHECK ---
    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.rtrab_logo),
            contentDescription = "RTRAB-C Logo",
            modifier = Modifier.size(180.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Text(
                    text = if (passwordVisible) "HIDE" else "SHOW",
                    color = Color(0xFF1565C0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { passwordVisible = !passwordVisible }.padding(8.dp)
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isLoading = true
                coroutineScope.launch {
                    if (isOnline()) {

                        // --- 1. ONLINE MODE: CONNECT TO SUPABASE ---
                        try {
                            // Correct Supabase Authentication Syntax
                            supabase.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                                this.email = email
                                this.password = password
                            }

                            // SAVE THE SECURE HASH FOR FUTURE OFFLINE USE
                            sharedPref.edit()
                                .putString("cached_email", email)
                                .putString("cached_password_hash", hashPassword(password))
                                .apply()

                            isLoading = false
                            Toast.makeText(context, "Cloud Login Successful", Toast.LENGTH_SHORT).show()
                            onLoginSuccess("HOME")
                        } catch (e: Exception) {
                            isLoading = false
                            Toast.makeText(context, "Login Failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // --- OFFLINE MODE: CHECK LOCAL VAULT ---
                        val savedEmail = sharedPref.getString("cached_email", "")
                        val savedHash = sharedPref.getString("cached_password_hash", "")

                        // Check if what they typed matches the secure hash
                        if (savedEmail == email && savedHash == hashPassword(password)) {
                            isLoading = false
                            Toast.makeText(context, "Offline Vault Unlocked!", Toast.LENGTH_SHORT).show()
                            onLoginSuccess("HOME")
                        } else {
                            isLoading = false
                            Toast.makeText(context, "Error: Incorrect Password or Not Cached.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            shape = RoundedCornerShape(8.dp),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "VERIFYING..." else "SECURE LOGIN", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Don't have an account? Sign Up",
            color = Color(0xFF1565C0),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onNavigateToSignUp() }
        )
    }
}