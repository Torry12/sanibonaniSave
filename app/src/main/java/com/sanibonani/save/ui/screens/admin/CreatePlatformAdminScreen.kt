package com.sanibonani.save.ui.screens.admin

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.viewmodel.CreatePlatformAdminViewModel

@Composable
fun CreatePlatformAdminScreen(
    onBack: () -> Unit,
    vm: CreatePlatformAdminViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title = "Create Admin Account",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cream)
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InfoBox(
                message = "This will create a new account with group administrative access. For security, subsequent platform admin creation is restricted to the canonical owner.",
                type = InfoType.WARNING
            )

            Spacer(Modifier.height(24.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SanibonaniTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Admin Email",
                        placeholder = "e.g. admin@sanibonani.com",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    SanibonaniTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        placeholder = "Enter secure password",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    SanibonaniTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = "Confirm Password",
                        placeholder = "Repeat password",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            if (state.isLoading) {
                CircularProgressIndicator(color = Forest)
            } else {
                SanibonaniButton(
                    text = "CREATE ADMINISTRATOR",
                    onClick = { vm.createPlatformAdmin(email, password, confirmPassword) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = email.isNotBlank() && password.length >= 6
                )
            }

            if (state.success) {
                Spacer(Modifier.height(16.dp))
                InfoBox("Administrative account created successfully!", InfoType.SUCCESS)
                
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    onBack()
                }
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                InfoBox(it, InfoType.ERROR)
            }
            
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onBack) {
                Text("CANCEL", color = MidGray, fontWeight = FontWeight.Bold)
            }
        }
    }
}
