package com.example

import android.widget.Toast
import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.ExperimentalLayoutApi

data class TankConfigData(
    val id: String,
    var name: String,
    val product: String,
    var capacity: String = ""
)

data class NozzleConfigData(
    val id: String,
    var name: String,
    var number: String,
    val product: String,
    val productId: String = "",
    val tankName: String,
    val tankId: String,
    var initialReading: String = "0"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String, String, List<String>, List<String>, String, String, String) -> Unit,
    onSkipLogin: () -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    // Existing Functionality States (Preserved)
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var mobileNumber by rememberSaveable { mutableStateOf("") }
    var isOtpSent by rememberSaveable { mutableStateOf(false) }
    var generatedOtp by rememberSaveable { mutableStateOf("") }
    var userEnteredOtp by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf("") }
    var forceResendingToken by remember { mutableStateOf<com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken?>(null) }
    var isOtpLoginMode by rememberSaveable { mutableStateOf(false) }
    var isOtpBypassEnabled by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var availableAccounts by remember { mutableStateOf<List<AccountOption>>(emptyList()) }
    var showAccountSelection by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var mobileNumberError by remember { mutableStateOf<String?>(null) }
    var otpError by remember { mutableStateOf<String?>(null) }
    var isPumpSetupActive by rememberSaveable { mutableStateOf(false) }
    var setupStep by rememberSaveable { mutableStateOf(1) } // 1: Basic, 2: Tanks, 3: Nozzles, 4: Readings
    var pumpName by rememberSaveable { mutableStateOf("") }
    var totalTanksInput by rememberSaveable { mutableStateOf("1") }
    
    // Tank configurations
    var tankLabels by remember { mutableStateOf(listOf("Tank 1")) }
    var tankProductTypes by remember { mutableStateOf(listOf("MS")) }
    var tankNozzleCounts by remember { mutableStateOf(listOf(1)) }
    var tankNozzleReadings by remember { mutableStateOf(listOf(listOf("0"))) }
    var tankNozzleNames by remember { mutableStateOf(listOf(listOf(""))) }
    var tankNozzleNumbers by remember { mutableStateOf(listOf(listOf("1"))) }

    // New Hierarchical Setup States
    var selectedProducts by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tankConfigs by remember { mutableStateOf<List<TankConfigData>>(emptyList()) }
    var nozzleConfigs by remember { mutableStateOf<List<NozzleConfigData>>(emptyList()) }

    // Logic Functions (Preserved & Re-connected)
    val resetFormStates = {
        usernameError = null; mobileNumberError = null; otpError = null; isOtpSent = false
        userEnteredOtp = ""; generatedOtp = ""; isOtpLoginMode = false; isPumpSetupActive = false
        setupStep = 1; pumpName = ""; totalTanksInput = "1"
        tankLabels = listOf("Tank 1"); tankProductTypes = listOf("MS")
        tankNozzleCounts = listOf(1); tankNozzleReadings = listOf(listOf("0"))
        tankNozzleNames = listOf(listOf("")); tankNozzleNumbers = listOf(listOf("1"))
        selectedProducts = emptySet(); tankConfigs = emptyList(); nozzleConfigs = emptyList()
        availableAccounts = emptyList(); showAccountSelection = false
    }

    val proceedWithAccount: (AccountOption) -> Unit = { selectedAccount ->
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val pumpInfo = com.example.database.FirestoreRepository.getPumpInfo(selectedAccount.ownerAdminPhone)
            val registeredList = com.example.database.FirestoreRepository.getRegisteredNozzles(selectedAccount.ownerAdminPhone)
            val finalMsLabels = registeredList.filter { it.nozzleType == "MS" }.sortedBy { it.nozzleIndex }.map { it.label }
            val finalHsdLabels = registeredList.filter { it.nozzleType == "HSD" }.sortedBy { it.nozzleIndex }.map { it.label }
            withContext(Dispatchers.Main) {
                isLoading = false
                onLoginSuccess(selectedAccount.username, pumpInfo?.pumpName ?: selectedAccount.pumpName, finalMsLabels, finalHsdLabels, selectedAccount.mobileNumber, selectedAccount.ownerAdminPhone, selectedAccount.role)
            }
        }
    }

    val completeLoginFlow: suspend (String, String) -> Unit = { formattedMobile, rawMobile ->
        isLoading = true
        val options = mutableListOf<AccountOption>()
        withContext(Dispatchers.IO) {
            val remoteResult = com.example.database.FirestoreUserManager.getUserMemberships(formattedMobile)
            remoteResult.onSuccess { memberships ->
                memberships.forEach { m ->
                    val adminPhone = m["adminPhone"] as? String ?: ""
                    val pName = m["pumpName"] as? String ?: "Cloud Pump"
                    val role = m["role"] as? String ?: "Staff"
                    val cloudUsername = m["username"] as? String ?: "Cloud User"
                    
                    val desc = when(role.uppercase()) {
                        "ADMIN" -> "Full administrative access"
                        "MANAGER" -> "Manager access"
                        "CA" -> "Cashier/Audit access"
                        else -> "$role access"
                    }

                    if (options.none { it.ownerAdminPhone == adminPhone && it.role == role }) {
                        options.add(AccountOption(cloudUsername, pName, role, desc, formattedMobile, adminPhone))
                    }
                }
            }
            if (options.none { it.role == "ADMIN" && it.ownerAdminPhone == formattedMobile }) {
                val checkResult = com.example.database.FirestoreUserManager.checkUserInFirestore(formattedMobile)
                checkResult.onSuccess { userData ->
                    if (userData != null) {
                        val storedUsername = userData["username"] as? String ?: "Admin"
                        options.add(AccountOption(storedUsername, storedUsername, "ADMIN", "Full administrative access", formattedMobile, formattedMobile))
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            isLoading = false
            if (options.isEmpty()) mobileNumberError = "No account found in Cloud."
            else if (options.size == 1) proceedWithAccount(options.first())
            else { availableAccounts = options; showAccountSelection = true; isOtpSent = false }
        }
    }

    val dispatchOtpFlow: suspend (String, String) -> Unit = { formattedMobile, rawMobile ->
        if (isOtpBypassEnabled) {
            isLoading = false; verificationId = "simulated"; generatedOtp = "1234"; isOtpSent = true
        } else {
            val activity = context as? Activity
            if (activity != null) {
                com.example.database.FirebasePhoneAuthManager.startPhoneNumberVerification(
                    activity, formattedMobile, object : com.example.database.FirebasePhoneAuthManager.VerificationCallbacks {
                        override fun onCodeSent(id: String, token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken) {
                            isLoading = false; verificationId = id; forceResendingToken = token; isOtpSent = true
                        }
                        override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                            isLoading = true
                            com.example.database.FirebasePhoneAuthManager.signInWithCredential(credential) { result ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    isLoading = false
                                    result.onSuccess {
                                        if (isSignUpMode) { pumpName = username.trim(); isPumpSetupActive = true; isOtpSent = false }
                                        else completeLoginFlow(formattedMobile, rawMobile)
                                    }.onFailure { otpError = it.localizedMessage }
                                }
                            }
                        }
                        override fun onVerificationFailed(e: Exception) { isLoading = false; mobileNumberError = e.localizedMessage }
                    }
                )
            }
        }
    }

    val handleAuth = {
        focusManager.clearFocus()
        val u = username.trim()
        val m = mobileNumber.trim()
        if (isOtpSent) {
            val entered = userEnteredOtp.trim()
            if (entered == "1234" && verificationId == "simulated") {
                coroutineScope.launch {
                    val formatted = com.example.database.FirestoreUserManager.formatMobileNumber(m)
                    if (isSignUpMode) { pumpName = u; isPumpSetupActive = true; isOtpSent = false }
                    else completeLoginFlow(formatted, m)
                }
            } else if (verificationId != "simulated") {
                isLoading = true
                val credential = com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId, entered)
                com.example.database.FirebasePhoneAuthManager.signInWithCredential(credential) { result ->
                    coroutineScope.launch(Dispatchers.Main) {
                        isLoading = false
                        result.onSuccess {
                            if (isSignUpMode) { pumpName = u; isPumpSetupActive = true; isOtpSent = false }
                            else { val f = com.example.database.FirestoreUserManager.formatMobileNumber(m); completeLoginFlow(f, m) }
                        }.onFailure { otpError = it.localizedMessage }
                    }
                }
            }
        } else {
            if (m.isNotEmpty()) {
                isLoading = true
                coroutineScope.launch {
                    val f = com.example.database.FirestoreUserManager.formatMobileNumber(m)
                    if (isSignUpMode) {
                        val check = com.example.database.FirestoreUserManager.checkUserInFirestore(f)
                        if (check.getOrNull() != null) { mobileNumberError = "Already registered"; isLoading = false }
                        else dispatchOtpFlow(f, m)
                    } else dispatchOtpFlow(f, m)
                }
            }
        }
    }

    val handlePumpSetupComplete = {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val formattedMobile = com.example.database.FirestoreUserManager.formatMobileNumber(mobileNumber)
                
                // Use the new hierarchical nozzle configs
                val allRegisteredNozzles = nozzleConfigs.mapIndexed { index, config ->
                    RegisteredNozzle(
                        mobileNumber = formattedMobile,
                        nozzleType = config.product,
                        label = "${config.name} (#${config.number})",
                        nozzleName = config.name,
                        nozzleNumber = config.number,
                        nozzleId = config.id,
                        productId = config.productId,
                        tankName = config.tankName,
                        tankId = config.tankId,
                        nozzleIndex = index,
                        initialReading = config.initialReading.toDoubleOrNull() ?: 0.0
                    )
                }

                val msNozzles = allRegisteredNozzles.filter { it.nozzleType.contains("Petrol", ignoreCase = true) || it.nozzleType.contains("Premium", ignoreCase = true) }
                val hsdNozzles = allRegisteredNozzles.filter { it.nozzleType.contains("Diesel", ignoreCase = true) }
                
                val msTanks = tankConfigs.filter { it.product.contains("Petrol", ignoreCase = true) || it.product.contains("Premium", ignoreCase = true) }
                val hsdTanks = tankConfigs.filter { it.product.contains("Diesel", ignoreCase = true) }

                val newPumpInfo = PumpInfo(
                    mobileNumber = formattedMobile,
                    pumpName = pumpName,
                    numMsNozzles = msNozzles.size,
                    msNozzleLabels = msNozzles.joinToString(",") { it.label },
                    numHsdNozzles = hsdNozzles.size,
                    hsdNozzleLabels = hsdNozzles.joinToString(",") { it.label },
                    numMsTanks = msTanks.size,
                    numHsdTanks = hsdTanks.size,
                    msTankLabels = msTanks.joinToString(",") { it.name },
                    hsdTankLabels = hsdTanks.joinToString(",") { it.name },
                    productNames = selectedProducts.joinToString(","),
                    updatedAt = System.currentTimeMillis()
                )
                
                com.example.database.FirestoreRepository.savePumpInfo(newPumpInfo)
                com.example.database.FirestoreRepository.saveNozzles(formattedMobile, allRegisteredNozzles)
                
                com.example.database.FirestoreUserManager.saveUserToFirestore(formattedMobile, username, "", "")
                
                withContext(Dispatchers.Main) {
                    isLoading = false; isSignUpMode = false; isPumpSetupActive = false; isOtpSent = false
                    Toast.makeText(context, "Setup Success!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    // Overhaul UI Design Elements
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = Math.PI.toFloat() * 2f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Restart), label = "phase"
    )

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = if (isDarkTheme) listOf(Color(0xFF020617), Color(0xFF0F172A))
                else listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))
            )
        )
    ) {
        // Floating Mesh Background (Linear/Stripe style)
        Canvas(modifier = Modifier.fillMaxSize().blur(120.dp).alpha(if (isDarkTheme) 0.3f else 0.4f)) {
            val w = size.width; val h = size.height
            drawCircle(
                brush = Brush.radialGradient(colors = listOf(Color(0xFF3B82F6), Color.Transparent)),
                radius = w * 0.8f, center = Offset(w * 0.1f + (Math.sin(phase.toDouble()) * 100).toFloat(), h * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(colors = listOf(Color(0xFF8B5CF6), Color.Transparent)),
                radius = w * 0.7f, center = Offset(w * 0.9f - (Math.cos(phase.toDouble()) * 100).toFloat(), h * 0.3f)
            )
            drawCircle(
                brush = Brush.radialGradient(colors = listOf(Color(0xFFEC4899), Color.Transparent)),
                radius = w * 0.6f, center = Offset(w * 0.5f, h * 0.8f + (Math.sin(phase.toDouble()) * 50).toFloat())
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Premium Header Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = Color(0xFF3B82F6), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Waves, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                    Text("PumpManager", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = (-0.5).sp))
                }
                IconButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onThemeChange(!isDarkTheme) },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape).size(40.dp)
                ) {
                    Icon(imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Animated Branding Logo
            Box(
                modifier = Modifier.size(110.dp).graphicsLayer {
                    rotationZ = (Math.sin(phase.toDouble()) * 5).toFloat()
                    translationY = (Math.cos(phase.toDouble() * 2) * 10).toFloat()
                }.background(Brush.linearGradient(colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(54.dp), tint = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Identity Access", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp))
            Text("Smart Station Reconciliation System", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), letterSpacing = 0.5.sp))

            Spacer(modifier = Modifier.height(48.dp))

            // Glassmorphism Card
            Card(
                modifier = Modifier.fillMaxWidth(if (context.resources.configuration.screenWidthDp > 600) 0.5f else 0.9f).padding(bottom = 80.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                AnimatedContent(
                    targetState = if (isPumpSetupActive) "setup" else if (isOtpSent) "otp" else if (showAccountSelection) "selection" else "login",
                    transitionSpec = { (fadeIn(tween(500)) + slideInHorizontally { it / 2 }).togetherWith(fadeOut(tween(500)) + slideOutHorizontally { -it / 2 }) },
                    label = "auth_flow"
                ) { state ->
                    Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        when (state) {
                            "login" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = if (isSignUpMode) "New Station" else "Login Portal", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                                    Text(text = "Provide your authorized mobile number", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isSignUpMode) PremiumTextField(value = username, onValueChange = { username = it; usernameError = null }, label = "Pump Name", icon = Icons.Default.Place, error = usernameError)
                                PremiumTextField(value = mobileNumber, onValueChange = { mobileNumber = it; mobileNumberError = null }, label = "Mobile Number (+91...)", icon = Icons.Default.Phone, error = mobileNumberError, keyboardType = KeyboardType.Phone, onDone = { handleAuth() })
                                PremiumButton(text = "Authorize & Send OTP", isLoading = isLoading, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); handleAuth() })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); isSignUpMode = !isSignUpMode; resetFormStates() }, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = if (isSignUpMode) "Already verified? Sign In" else "Register new station", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = Color(0xFF3B82F6)))
                                }
                            }
                            "otp" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = "Verification", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                                    Text(text = "Enter 4-digit code sent to $mobileNumber", style = MaterialTheme.typography.bodySmall)
                                }
                                PremiumTextField(value = userEnteredOtp, onValueChange = { userEnteredOtp = it; otpError = null }, label = "4-Digit OTP", icon = Icons.Default.Pin, error = otpError, keyboardType = KeyboardType.Number, onDone = { handleAuth() })
                                PremiumButton(text = "Verify Access", isLoading = isLoading, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); handleAuth() })
                                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); isOtpSent = false }, modifier = Modifier.fillMaxWidth()) { Text("Change Phone Number") }
                            }
                            "selection" -> {
                                Text(text = "Choose Account", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    availableAccounts.forEach { account -> AccountSelectionRow(account, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); proceedWithAccount(account) }) }
                                }
                                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); resetFormStates() }, modifier = Modifier.fillMaxWidth()) { Text("Abort", color = MaterialTheme.colorScheme.error) }
                            }
                            "setup" -> {
                                PetrolPumpSetupFlow(
                                    setupStep = setupStep,
                                    onStepChange = { setupStep = it },
                                    pumpName = pumpName,
                                    onPumpNameChange = { pumpName = it },
                                    totalTanksInput = totalTanksInput,
                                    onTotalTanksChange = { totalTanksInput = it },
                                    tankLabels = tankLabels,
                                    onTankLabelsChange = { tankLabels = it },
                                    tankProductTypes = tankProductTypes,
                                    onTankProductTypesChange = { tankProductTypes = it },
                                    tankNozzleCounts = tankNozzleCounts,
                                    onTankNozzleCountsChange = { tankNozzleCounts = it },
                                    tankNozzleReadings = tankNozzleReadings,
                                    onTankNozzleReadingsChange = { tankNozzleReadings = it },
                                    tankNozzleNames = tankNozzleNames,
                                    onTankNozzleNamesChange = { tankNozzleNames = it },
                                    tankNozzleNumbers = tankNozzleNumbers,
                                    onTankNozzleNumbersChange = { tankNozzleNumbers = it },
                                    selectedProducts = selectedProducts,
                                    onSelectedProductsChange = { selectedProducts = it },
                                    tankConfigs = tankConfigs,
                                    onTankConfigsChange = { tankConfigs = it },
                                    nozzleConfigs = nozzleConfigs,
                                    onNozzleConfigsChange = { nozzleConfigs = it },
                                    isLoading = isLoading,
                                    onComplete = { handlePumpSetupComplete() },
                                    onBack = { isPumpSetupActive = false }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Column(modifier = Modifier.padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "POWERED BY", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)))
                Text(text = "GenovaCare Innovations", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6)))
            }
        }
    }
}

@Composable
fun PremiumTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, error: String? = null, keyboardType: KeyboardType = KeyboardType.Text, onDone: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = if (error != null) MaterialTheme.colorScheme.error else Color(0xFF3B82F6).copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) },
            isError = error != null, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3B82F6), unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        if (error != null) Text(text = error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun PremiumButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "scale")
    Button(
        onClick = onClick, enabled = !isLoading, interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().height(58.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
    ) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text = text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
    }
}

@Composable
fun AccountSelectionRow(account: AccountOption, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = when(account.role.uppercase()) {
                            "ADMIN" -> Color(0xFF2563EB).copy(alpha = 0.1f)
                            "MANAGER" -> Color(0xFFF59E0B).copy(alpha = 0.1f)
                            else -> Color(0xFF10B981).copy(alpha = 0.1f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(account.role.uppercase()) {
                        "ADMIN" -> Icons.Default.AdminPanelSettings
                        "MANAGER" -> Icons.Default.Person
                        else -> Icons.Default.LocalGasStation
                    },
                    contentDescription = null,
                    tint = when(account.role.uppercase()) {
                        "ADMIN" -> Color(0xFF2563EB)
                        "MANAGER" -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = account.role.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = when(account.role.uppercase()) {
                            "ADMIN" -> Color(0xFF2563EB)
                            "MANAGER" -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        }
                    )
                )
                Text(
                    text = account.pumpName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = account.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetrolPumpSetupFlow(
    setupStep: Int,
    onStepChange: (Int) -> Unit,
    pumpName: String,
    onPumpNameChange: (String) -> Unit,
    totalTanksInput: String,
    onTotalTanksChange: (String) -> Unit,
    tankLabels: List<String>,
    onTankLabelsChange: (List<String>) -> Unit,
    tankProductTypes: List<String>,
    onTankProductTypesChange: (List<String>) -> Unit,
    tankNozzleCounts: List<Int>,
    onTankNozzleCountsChange: (List<Int>) -> Unit,
    tankNozzleReadings: List<List<String>>,
    onTankNozzleReadingsChange: (List<List<String>>) -> Unit,
    tankNozzleNames: List<List<String>>,
    onTankNozzleNamesChange: (List<List<String>>) -> Unit,
    tankNozzleNumbers: List<List<String>>,
    onTankNozzleNumbersChange: (List<List<String>>) -> Unit,
    selectedProducts: Set<String>,
    onSelectedProductsChange: (Set<String>) -> Unit,
    tankConfigs: List<TankConfigData>,
    onTankConfigsChange: (List<TankConfigData>) -> Unit,
    nozzleConfigs: List<NozzleConfigData>,
    onNozzleConfigsChange: (List<NozzleConfigData>) -> Unit,
    isLoading: Boolean,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.heightIn(max = 600.dp).verticalScroll(scrollState)) {
        Text("Station Registration", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black))
        
        when (setupStep) {
            1 -> {
                // STEP 1: Basic Info & Products
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Step 1: Basic & Products", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    PremiumTextField(value = pumpName, onValueChange = onPumpNameChange, label = "Pump Name (Business Name)", icon = Icons.Default.Business)
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    Text("Select Products Sold:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    val standardProducts = listOf("Petrol", "Diesel", "Premium Petrol", "CNG", "AdBlue")
                    
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        standardProducts.forEach { product ->
                            FilterChip(
                                selected = selectedProducts.contains(product),
                                onClick = {
                                    onSelectedProductsChange(if (selectedProducts.contains(product)) selectedProducts - product else selectedProducts + product)
                                },
                                label = { Text(product) }
                            )
                        }
                    }
                    
                    var customProduct by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customProduct,
                            onValueChange = { customProduct = it },
                            label = { Text("Custom Product") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        IconButton(
                            onClick = {
                                if (customProduct.isNotBlank()) {
                                    onSelectedProductsChange(selectedProducts + customProduct.trim())
                                    customProduct = ""
                                }
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, null)
                        }
                    }
                    
                    if (selectedProducts.isNotEmpty()) {
                        Text("Active Products:", style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedProducts.forEach { product ->
                                AssistChip(
                                    onClick = { onSelectedProductsChange(selectedProducts - product) },
                                    label = { Text(product) },
                                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                    
                    PremiumButton(text = "Next: Configure Tanks", isLoading = false) {
                        if (pumpName.isBlank()) {
                            Toast.makeText(context, "Pump name is required", Toast.LENGTH_SHORT).show()
                            return@PremiumButton
                        }
                        if (selectedProducts.isEmpty()) {
                            Toast.makeText(context, "Select at least one product", Toast.LENGTH_SHORT).show()
                            return@PremiumButton
                        }
                        // Initialize tanks if needed
                        if (tankConfigs.isEmpty()) {
                            val initialTanks = selectedProducts.map { product ->
                                TankConfigData(id = "TANK_${product.uppercase()}_1", name = "$product Tank 1", product = product)
                            }
                            onTankConfigsChange(initialTanks)
                        }
                        onStepChange(2)
                    }
                }
            }
            2 -> {
                // STEP 2: Configure Tanks for Each Product
                Text("Step 2: Configure Tanks", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                
                selectedProducts.forEach { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(product, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                val productTanks = tankConfigs.filter { it.product == product }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = {
                                        if (productTanks.size > 1) {
                                            val toRemove = productTanks.last()
                                            onTankConfigsChange(tankConfigs - toRemove)
                                        }
                                    }) { Icon(Icons.Default.Remove, null) }
                                    Text("${productTanks.size} Tanks", fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        val newIdx = productTanks.size + 1
                                        val newTank = TankConfigData(id = "TANK_${product.uppercase()}_$newIdx", name = "$product Tank $newIdx", product = product)
                                        onTankConfigsChange(tankConfigs + newTank)
                                    }) { Icon(Icons.Default.Add, null) }
                                }
                            }
                            
                            tankConfigs.filter { it.product == product }.forEach { tank ->
                                OutlinedTextField(
                                    value = tank.name,
                                    onValueChange = { newVal ->
                                        val updated = tankConfigs.toMutableList()
                                        val idx = updated.indexOfFirst { it.id == tank.id }
                                        if (idx != -1) {
                                            updated[idx] = updated[idx].copy(name = newVal)
                                            onTankConfigsChange(updated)
                                        }
                                    },
                                    label = { Text("Tank Name (ID: ${tank.id})") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
                
                PremiumButton(text = "Next: Configure Nozzles", isLoading = false) {
                    // Initialize nozzles if needed
                    if (nozzleConfigs.isEmpty()) {
                        val initialNozzles = tankConfigs.map { tank ->
                            NozzleConfigData(
                                id = "NOZ_${tank.id}_1",
                                name = "${tank.name} Nozzle 1",
                                number = (tankConfigs.indexOf(tank) + 1).toString(),
                                product = tank.product,
                                productId = "PROD_${tank.product.uppercase().replace(" ", "_")}",
                                tankName = tank.name,
                                tankId = tank.id
                            )
                        }
                        onNozzleConfigsChange(initialNozzles)
                    }
                    onStepChange(3)
                }
                TextButton(onClick = { onStepChange(1) }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
            3 -> {
                // STEP 3: Configure Nozzles for Each Tank
                Text("Step 3: Configure Nozzles", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                
                tankConfigs.forEach { tank ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text(tank.name, fontWeight = FontWeight.Black, color = Color(0xFF3B82F6))
                                    Text(tank.product, style = MaterialTheme.typography.labelSmall)
                                }
                                val tankNozzles = nozzleConfigs.filter { it.tankId == tank.id }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = {
                                        if (tankNozzles.size > 1) {
                                            val toRemove = tankNozzles.last()
                                            onNozzleConfigsChange(nozzleConfigs - toRemove)
                                        }
                                    }) { Icon(Icons.Default.Remove, null) }
                                    Text("${tankNozzles.size} Nozzles", fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        val newIdx = tankNozzles.size + 1
                                        val newNoz = NozzleConfigData(
                                            id = "NOZ_${tank.id}_$newIdx",
                                            name = "${tank.name} Nozzle $newIdx",
                                            number = "",
                                            product = tank.product,
                                            productId = "PROD_${tank.product.uppercase().replace(" ", "_")}",
                                            tankName = tank.name,
                                            tankId = tank.id
                                        )
                                        onNozzleConfigsChange(nozzleConfigs + newNoz)
                                    }) { Icon(Icons.Default.Add, null) }
                                }
                            }
                            
                            nozzleConfigs.filter { it.tankId == tank.id }.forEach { nozzle ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = nozzle.number,
                                        onValueChange = { newVal ->
                                            val updated = nozzleConfigs.toMutableList()
                                            val idx = updated.indexOfFirst { it.id == nozzle.id }
                                            if (idx != -1) {
                                                updated[idx] = updated[idx].copy(number = newVal.filter { it.isDigit() })
                                                onNozzleConfigsChange(updated)
                                            }
                                        },
                                        label = { Text("No.") },
                                        modifier = Modifier.weight(0.3f),
                                        shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = nozzle.name,
                                        onValueChange = { newVal ->
                                            val updated = nozzleConfigs.toMutableList()
                                            val idx = updated.indexOfFirst { it.id == nozzle.id }
                                            if (idx != -1) {
                                                updated[idx] = updated[idx].copy(name = newVal)
                                                onNozzleConfigsChange(updated)
                                            }
                                        },
                                        label = { Text("Nozzle Name") },
                                        modifier = Modifier.weight(0.7f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                PremiumButton(text = "Next: Initial Readings", isLoading = false) {
                    // VALIDATION
                    val allNumbers = nozzleConfigs.map { it.number }
                    if (allNumbers.any { it.isBlank() }) {
                        Toast.makeText(context, "All nozzle numbers are required", Toast.LENGTH_SHORT).show()
                        return@PremiumButton
                    }
                    if (allNumbers.distinct().size != allNumbers.size) {
                        Toast.makeText(context, "Nozzle numbers must be unique across the station", Toast.LENGTH_SHORT).show()
                        return@PremiumButton
                    }
                    val allNames = nozzleConfigs.map { it.name }
                    if (allNames.any { it.isBlank() }) {
                        Toast.makeText(context, "All nozzle names are required", Toast.LENGTH_SHORT).show()
                        return@PremiumButton
                    }
                    if (allNames.distinct().size != allNames.size) {
                        Toast.makeText(context, "Nozzle names must be unique across the station", Toast.LENGTH_SHORT).show()
                        return@PremiumButton
                    }
                    onStepChange(4)
                }
                TextButton(onClick = { onStepChange(2) }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
            4 -> {
                // STEP 4: Initial Readings
                Text("Step 4: Initial Meter Readings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                
                nozzleConfigs.groupBy { it.tankName }.forEach { (tankName, nozzles) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(tankName, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3B82F6))
                        nozzles.forEach { nozzle ->
                            OutlinedTextField(
                                value = nozzle.initialReading,
                                onValueChange = { newVal ->
                                    val updated = nozzleConfigs.toMutableList()
                                    val idx = updated.indexOfFirst { it.id == nozzle.id }
                                    if (idx != -1) {
                                        updated[idx] = updated[idx].copy(initialReading = newVal.filter { it.isDigit() || it == '.' })
                                        onNozzleConfigsChange(updated)
                                    }
                                },
                                label = { Text("${nozzle.name} (#${nozzle.number}) Initial Reading") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
                
                PremiumButton(text = "Complete Registration", isLoading = isLoading, onClick = onComplete)
                TextButton(onClick = { onStepChange(3) }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
        }
        
        if (setupStep == 1) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Go Back", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun NozzleCountSelector(label: String, count: Int, onCountChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = { if (count > 1) onCountChange(count - 1) }, modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
            }
            Text("$count", fontWeight = FontWeight.Black)
            IconButton(onClick = { if (count < 15) onCountChange(count + 1) }, modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
