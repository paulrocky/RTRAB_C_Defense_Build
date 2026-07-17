package com.example.rtrab_c

// --- Supabase Imports ---
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from

// --- Android & System Imports ---
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// ==========================================
// DATA MODEL FOR DATABASE UPSERT
// ==========================================
@Serializable
data class UserProfileToSave(
    val id: String,
    val email: String,
    val name: String,
    val contact_info: String,
    val role: String
)

// ==========================================
// SCREEN: SIGN UP
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    supabase: SupabaseClient,
    onSignUpSuccess: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Input States
    var fullName by remember { mutableStateOf("") }
    var contactNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var confirmEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Moderator Security States
    var isModerator by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var secretCodeInput by remember { mutableStateOf("") }

    // UI Toggles & States
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val themeGreen = Color(0xFF0F8C3B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Spacer(modifier = Modifier.height(48.dp))

        // --- HEADER ---
        Text(
            text = "Create Account",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = themeGreen
        )

        Text(
            text = "Citizen Reporting Portal",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        // --- ERROR MESSAGE ---
        if (errorMessage.isNotEmpty()) {
            Surface(
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // --- INPUT FIELDS ---
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            placeholder = { Text("Full Name", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Gray,
                focusedBorderColor = themeGreen
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = contactNumber,
            onValueChange = { contactNumber = it },
            placeholder = { Text("Contact Number", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Gray,
                focusedBorderColor = themeGreen
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = { Text("Email Address", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Gray,
                focusedBorderColor = themeGreen
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmEmail,
            onValueChange = { confirmEmail = it },
            placeholder = { Text("Confirm Email Address", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Gray,
                focusedBorderColor = themeGreen
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("Password (Caps, Num, Special)", color = Color.Gray) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Text(
                    text = if (passwordVisible) "HIDE" else "SHOW",
                    color = themeGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { passwordVisible = !passwordVisible }.padding(8.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Gray,
                focusedBorderColor = themeGreen
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            placeholder = { Text("Confirm Password", color = Color.Gray) },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Text(
                    text = if (confirmPasswordVisible) "HIDE" else "SHOW",
                    color = themeGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { confirmPasswordVisible = !confirmPasswordVisible }.padding(8.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Gray,
                focusedBorderColor = themeGreen
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- CHECKBOX FOR MODERATOR (TRIGGERS POP-UP) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isModerator,
                onCheckedChange = { checked ->
                    if (checked) {
                        secretCodeInput = "" // Clear previous input
                        showSecurityDialog = true // Show the pop-up
                    } else {
                        isModerator = false // Uncheck normally
                    }
                },
                colors = CheckboxDefaults.colors(checkedColor = themeGreen)
            )
            Text(
                text = "Register as City Official (Moderator)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- REGISTER BUTTON ---
        Button(
            onClick = {
                errorMessage = ""

                // 1. Validation
                if (fullName.isBlank() || contactNumber.isBlank() || email.isBlank() || confirmEmail.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                    errorMessage = "Please fill in all fields."
                    return@Button
                }

                if (email != confirmEmail) {
                    errorMessage = "Email addresses do not match."
                    return@Button
                }

                if (password != confirmPassword) {
                    errorMessage = "Passwords do not match."
                    return@Button
                }

                if (password.length < 6) {
                    errorMessage = "Password must be at least 6 characters."
                    return@Button
                }

                val assignedRole = if (isModerator) "MODERATOR" else "CITIZEN"

                // 2. Perform Supabase Registration
                coroutineScope.launch {
                    isLoading = true
                    try {
                        // Create Auth Account
                        supabase.auth.signUpWith(Email) {
                            this.email = email
                            this.password = password
                        }

                        val userId = supabase.auth.currentUserOrNull()?.id

                        if (userId != null) {
                            val newDbProfile = UserProfileToSave(
                                id = userId,
                                email = email,
                                name = fullName,
                                contact_info = contactNumber,
                                role = assignedRole
                            )

                            // Save the full details to the 'users' table
                            supabase.from("users").upsert(newDbProfile)

                            // --- NO MORE EMAIL VERIFICATION PROMPT ---
                            Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()

                            onNavigateToLogin()
                        } else {
                            throw Exception("Failed to retrieve user ID after signup.")
                        }

                    } catch (e: Exception) {
                        errorMessage = e.message ?: "An unexpected error occurred."
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeGreen),
            shape = RoundedCornerShape(50),
            enabled = !isLoading
        ) {
            Text(
                text = if (isLoading) "REGISTERING..." else "REGISTER",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- BACK TO LOGIN LINK ---
        Text(
            text = "Already have an account? Login",
            color = Color(0xFF0066CC),
            fontSize = 13.sp,
            modifier = Modifier.clickable { onNavigateToLogin() }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ==========================================
    // SECURITY POP-UP DIALOG
    // ==========================================
    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = {
                showSecurityDialog = false
                isModerator = false // Uncheck if they tap outside
            },
            title = {
                Text(
                    text = "Security Verification",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF154360)
                )
            },
            text = {
                Column {
                    Text("Please enter the LGU authorization code to register as a moderator.", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = secretCodeInput,
                        onValueChange = { secretCodeInput = it },
                        placeholder = { Text("Enter code") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeGreen,
                            focusedLabelColor = themeGreen
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (secretCodeInput == "ADMIN2026") {
                            isModerator = true
                            showSecurityDialog = false
                            Toast.makeText(context, "Code Verified", Toast.LENGTH_SHORT).show()
                        } else {
                            isModerator = false
                            showSecurityDialog = false
                            Toast.makeText(context, "Invalid Authorization Code", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeGreen)
                ) {
                    Text("VERIFY", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSecurityDialog = false
                        isModerator = false // Uncheck if they cancel
                    }
                ) {
                    Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}