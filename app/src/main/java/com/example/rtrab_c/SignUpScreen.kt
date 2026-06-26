package com.example.rtrab_c

// --- Supabase Imports ---
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.*

// --- Android & System Imports ---
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(
    supabase: SupabaseClient,
    onSignUpSuccess: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var confirmEmail by remember { mutableStateOf("") }

    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var confirmPassword by remember { mutableStateOf("") }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var isModerator by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showModDialog by remember { mutableStateOf(false) }
    var modPasscode by remember { mutableStateOf("") }

    val isPasswordValid = password.length >= 6 && password.any { it.isUpperCase() } && password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Create Account", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Text("Citizen Reporting Portal", color = Color.Gray, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(32.dp))

        // --- EMAIL FIELD ---
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        // --- CONFIRM EMAIL FIELD ---
        OutlinedTextField(
            value = confirmEmail,
            onValueChange = { confirmEmail = it },
            label = { Text("Confirm Email Address") },
            modifier = Modifier.fillMaxWidth(),
            isError = email.isNotEmpty() && confirmEmail.isNotEmpty() && email != confirmEmail
        )
        Spacer(modifier = Modifier.height(8.dp))

        // --- PASSWORD FIELD ---
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (Caps, Num, Special)") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Text(
                    text = if (passwordVisible) "HIDE" else "SHOW",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { passwordVisible = !passwordVisible }
                        .padding(8.dp)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        // --- CONFIRM PASSWORD FIELD ---
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Text(
                    text = if (confirmPasswordVisible) "HIDE" else "SHOW",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { confirmPasswordVisible = !confirmPasswordVisible }
                        .padding(8.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            isError = password.isNotEmpty() && confirmPassword.isNotEmpty() && password != confirmPassword
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = isModerator, onCheckedChange = { if (it) showModDialog = true else isModerator = false }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFD32F2F)))
            Text("Register as City Official (Moderator)", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val currentEmail = email.trim()
                val currentConfirmEmail = confirmEmail.trim()
                val currentPassword = password
                val currentConfirmPassword = confirmPassword

                // --- VALIDATION CHECKS ---
                if (currentEmail.isEmpty() || currentConfirmEmail.isEmpty() || currentPassword.isEmpty() || currentConfirmPassword.isEmpty()) {
                    Toast.makeText(context, "Please fill in all fields.", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (currentEmail != currentConfirmEmail) {
                    Toast.makeText(context, "Email addresses do not match.", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (currentPassword != currentConfirmPassword) {
                    Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (!isPasswordValid) {
                    Toast.makeText(context, "Invalid Password requirements.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                isLoading = true
                coroutineScope.launch {
                    try {
                        // 1. Sign Up in Supabase Auth
                        supabase.auth.signUpWith(Email) {
                            this.email = currentEmail
                            this.password = currentPassword
                        }

                        // 2. Insert record into 'users' table using the correct Data Class
                        val user = supabase.auth.currentUserOrNull()
                        if (user != null) {
                            val roleStr = if (isModerator) "MODERATOR" else "CITIZEN"
                            val newUserRole = UserRole(id = user.id, email = currentEmail, role = roleStr)

                            supabase.from("users").insert(newUserRole)

                            isLoading = false
                            Toast.makeText(context, "Account Created!", Toast.LENGTH_SHORT).show()
                            onSignUpSuccess(if (isModerator) "MODERATOR_DASHBOARD" else "HOME")
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            enabled = !isLoading
        ) { Text(if (isLoading) "REGISTERING..." else "REGISTER", fontWeight = FontWeight.Bold) }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Already have an account? Login", color = Color(0xFF1976D2), modifier = Modifier.clickable { onNavigateToLogin() })
    }

    // Passcode Dialog
    if (showModDialog) {
        AlertDialog(
            onDismissRequest = {
                showModDialog = false
                isModerator = false
            },
            title = { Text("Official Verification", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Please enter the LGU authorization code to register as a moderator.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = modPasscode,
                        onValueChange = { modPasscode = it },
                        label = { Text("Passcode") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Corrected Capstone Passcode
                        if (modPasscode == "RtrabcPhenix") {
                            showModDialog = false
                            isModerator = true
                            Toast.makeText(context, "Code Accepted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Invalid Code", Toast.LENGTH_SHORT).show()
                            isModerator = false
                            showModDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("Verify") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showModDialog = false
                    isModerator = false
                }) { Text("Cancel", color = Color.Red) }
            }
        )
    }
}