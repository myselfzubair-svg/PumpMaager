package com.example

import android.widget.Toast
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.AppDatabase
import com.example.database.User
import com.example.database.LoginInfo
import com.example.database.PumpInfo
import com.example.database.RegisteredNozzle
import com.example.database.PhoneAccess
import com.example.database.MsNozzleReading
import com.example.database.HsdNozzleReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String, String, List<String>, List<String>, String) -> Unit,
    onSkipLogin: () -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Database accessors
    val db = remember { AppDatabase.getDatabase(context) }
    val userDao = remember { db.userDao() }
    val loginInfoDao = remember { db.loginInfoDao() }

    // Screen state
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var ownerName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var mobileNumber by rememberSaveable { mutableStateOf("") }

    // OTP Verification process states
    var isOtpSent by rememberSaveable { mutableStateOf(false) }
    var generatedOtp by rememberSaveable { mutableStateOf("") }
    var userEnteredOtp by rememberSaveable { mutableStateOf("") }
    var otpMethodUsed by rememberSaveable { mutableStateOf("Firebase Auth SMS") }
    var verificationId by rememberSaveable { mutableStateOf("") }
    var forceResendingToken by remember { mutableStateOf<com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken?>(null) }
    var isOtpLoginMode by rememberSaveable { mutableStateOf(false) }


    // SMS permission state & launcher
    var smsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val requestSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        smsPermissionGranted = isGranted
        if (isGranted) {
            Toast.makeText(context, "Direct Device SMS sending enabled!", Toast.LENGTH_SHORT).show()
        }
    }

    // Visual states
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showAppCheckDiagnostics by remember { mutableStateOf(false) }

    // Validation errors
    var usernameError by remember { mutableStateOf<String?>(null) }
    var ownerNameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var mobileNumberError by remember { mutableStateOf<String?>(null) }
    var otpError by remember { mutableStateOf<String?>(null) }

    // Pump configuration state
    var isPumpSetupActive by rememberSaveable { mutableStateOf(false) }
    var pumpName by rememberSaveable { mutableStateOf("") }
    var pumpNameError by remember { mutableStateOf<String?>(null) }
    var numMsTanks by rememberSaveable { mutableStateOf("1") }
    var numHsdTanks by rememberSaveable { mutableStateOf("1") }
    var msNozzlesCount by rememberSaveable { mutableStateOf(2) }
    var hsdNozzlesCount by rememberSaveable { mutableStateOf(2) }

    // Nozzle labels mapping
    var msLabels by remember { mutableStateOf((1..15).map { "MS Nozzle $it" }) }
    var hsdLabels by remember { mutableStateOf((1..15).map { "HSD Nozzle $it" }) }
    var msInitialReadings by remember { mutableStateOf((1..15).map { "0" }) }
    var hsdInitialReadings by remember { mutableStateOf((1..15).map { "0" }) }

    // Resets states when switching forms
    val resetFormStates = {
        usernameError = null
        ownerNameError = null
        passwordError = null
        confirmPasswordError = null
        mobileNumberError = null
        otpError = null
        isOtpSent = false
        userEnteredOtp = ""
        generatedOtp = ""
        isOtpLoginMode = false
        isPumpSetupActive = false
        pumpName = ""
        pumpNameError = null
        numMsTanks = "1"
        numHsdTanks = "1"
        msNozzlesCount = 2
        hsdNozzlesCount = 2
        msLabels = (1..15).map { "MS Nozzle $it" }
        hsdLabels = (1..15).map { "HSD Nozzle $it" }
        msInitialReadings = (1..15).map { "0" }
        hsdInitialReadings = (1..15).map { "0" }
        ownerName = ""
    }

    val completeLoginFlow: suspend (String, String) -> Unit = { formattedMobile, rawMobile ->
        val info = withContext(Dispatchers.IO) {
            loginInfoDao.getLoginInfoByMobileNumber(formattedMobile) ?: loginInfoDao.getLoginInfoByMobileNumber(rawMobile)
        }
        if (info != null) {
            withContext(Dispatchers.IO) {
                val pumpInfo = db.pumpInfoDao().getPumpInfoByMobile(info.mobileNumber)
                val pumpNameString = pumpInfo?.pumpName ?: info.username
                
                val existingNozzles = db.registeredNozzleDao().getNozzlesByPumpMobile(info.mobileNumber)
                if (existingNozzles.isEmpty() && pumpInfo != null) {
                    val msList = pumpInfo.msNozzleLabels.split(",").filter { it.isNotBlank() }
                    val hsdList = pumpInfo.hsdNozzleLabels.split(",").filter { it.isNotBlank() }
                    val toInsert = mutableListOf<RegisteredNozzle>()
                    msList.forEachIndexed { idx, label ->
                        toInsert.add(RegisteredNozzle(mobileNumber = info.mobileNumber, nozzleType = "MS", label = label, nozzleIndex = idx))
                    }
                    hsdList.forEachIndexed { idx, label ->
                        toInsert.add(RegisteredNozzle(mobileNumber = info.mobileNumber, nozzleType = "HSD", label = label, nozzleIndex = idx))
                    }
                    db.registeredNozzleDao().insertNozzles(toInsert)
                }
                
                val registeredList = db.registeredNozzleDao().getNozzlesByPumpMobile(info.mobileNumber)
                val finalMsLabels = if (registeredList.isNotEmpty()) {
                    registeredList.filter { it.nozzleType == "MS" }.sortedBy { it.nozzleIndex }.map { it.label }
                } else {
                    pumpInfo?.msNozzleLabels?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                }
                val finalHsdLabels = if (registeredList.isNotEmpty()) {
                    registeredList.filter { it.nozzleType == "HSD" }.sortedBy { it.nozzleIndex }.map { it.label }
                } else {
                    pumpInfo?.hsdNozzleLabels?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                }
                
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(
                        context,
                        "Logged in successfully via OTP! Registered phone: ${info.mobileNumber}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onLoginSuccess(info.username, pumpNameString, finalMsLabels, finalHsdLabels, info.mobileNumber)
                }
            }
        } else {
            val checkResult = com.example.database.FirestoreUserManager.checkUserInFirestore(formattedMobile)
            checkResult.onSuccess { userData ->
                if (userData != null) {
                    val storedUsername = userData["username"] as? String ?: "D R Inamdar Petroleum"
                    val storedPasswordHash = userData["passwordHash"] as? String ?: ""
                    withContext(Dispatchers.IO) {
                        val loginInfo = LoginInfo(
                            mobileNumber = formattedMobile,
                            username = storedUsername,
                            passwordHash = storedPasswordHash
                        )
                        loginInfoDao.insertLoginInfo(loginInfo)
                        userDao.insertUser(User(username = formattedMobile, passwordHash = storedPasswordHash))
                        
                        com.example.database.FirestoreSyncManager.checkAndDownloadLoginInfo(context, formattedMobile)
                        
                        val pumpInfo = db.pumpInfoDao().getPumpInfoByMobile(formattedMobile)
                        val pumpNameString = pumpInfo?.pumpName ?: storedUsername
                        
                        val existingNozzles = db.registeredNozzleDao().getNozzlesByPumpMobile(formattedMobile)
                        if (existingNozzles.isEmpty() && pumpInfo != null) {
                            val msList = pumpInfo.msNozzleLabels.split(",").filter { it.isNotBlank() }
                            val hsdList = pumpInfo.hsdNozzleLabels.split(",").filter { it.isNotBlank() }
                            val toInsert = mutableListOf<RegisteredNozzle>()
                            msList.forEachIndexed { idx, label ->
                                toInsert.add(RegisteredNozzle(mobileNumber = formattedMobile, nozzleType = "MS", label = label, nozzleIndex = idx))
                            }
                            hsdList.forEachIndexed { idx, label ->
                                toInsert.add(RegisteredNozzle(mobileNumber = formattedMobile, nozzleType = "HSD", label = label, nozzleIndex = idx))
                            }
                            db.registeredNozzleDao().insertNozzles(toInsert)
                        }
                        
                        val registeredList = db.registeredNozzleDao().getNozzlesByPumpMobile(formattedMobile)
                        val finalMsLabels = if (registeredList.isNotEmpty()) {
                            registeredList.filter { it.nozzleType == "MS" }.sortedBy { it.nozzleIndex }.map { it.label }
                        } else {
                            pumpInfo?.msNozzleLabels?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                        }
                        val finalHsdLabels = if (registeredList.isNotEmpty()) {
                            registeredList.filter { it.nozzleType == "HSD" }.sortedBy { it.nozzleIndex }.map { it.label }
                        } else {
                            pumpInfo?.hsdNozzleLabels?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                        }
                        
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            Toast.makeText(
                                context,
                                "Logged in successfully via OTP! Registered phone: $formattedMobile",
                                Toast.LENGTH_SHORT
                            ).show()
                            onLoginSuccess(storedUsername, pumpNameString, finalMsLabels, finalHsdLabels, formattedMobile)
                        }
                    }
                } else {
                    coroutineScope.launch(Dispatchers.Main) {
                        isLoading = false
                        mobileNumberError = "This mobile number is not registered in the cloud database. Please sign up."
                    }
                }
            }.onFailure { e ->
                coroutineScope.launch(Dispatchers.Main) {
                    isLoading = false
                    mobileNumberError = "Firestore login check failed: ${e.localizedMessage}"
                }
            }
        }
    }

    val dispatchOtpFlow: suspend (String, String) -> Unit = { formattedMobile, rawMobile ->
        if (otpMethodUsed == "Simulated (Demo Mode)") {
            val code = (1000..9999).random().toString()
            delay(1000)
            withContext(Dispatchers.Main) {
                generatedOtp = code
                isOtpSent = true
                isLoading = false
                Toast.makeText(context, "OTP (Simulated) dispatched!", Toast.LENGTH_SHORT).show()
            }
        } else {
            val activity = context as? android.app.Activity
            if (activity != null) {
                com.example.database.FirebasePhoneAuthManager.startPhoneNumberVerification(
                    activity,
                    formattedMobile,
                    object : com.example.database.FirebasePhoneAuthManager.VerificationCallbacks {
                        override fun onCodeSent(id: String, token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken) {
                            coroutineScope.launch(Dispatchers.Main) {
                                isLoading = false
                                verificationId = id
                                forceResendingToken = token
                                isOtpSent = true
                                Toast.makeText(context, "OTP sent to $formattedMobile via Firebase!", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                            coroutineScope.launch(Dispatchers.Main) {
                                isLoading = true
                            }
                            com.example.database.FirebasePhoneAuthManager.signInWithCredential(credential) { result ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    isLoading = false
                                    result.onSuccess {
                                        if (isSignUpMode) {
                                            val cleanedMobile = rawMobile.filter { it.isDigit() }
                                            val rawOwnerName = ownerName.trim()
                                            val firstThree = if (rawOwnerName.length >= 3) rawOwnerName.take(3) else rawOwnerName
                                            val lastSix = if (cleanedMobile.length >= 6) cleanedMobile.takeLast(6) else cleanedMobile
                                            val generatedPassword = firstThree + lastSix
                                            password = generatedPassword
                                            confirmPassword = generatedPassword
                                            pumpName = username.trim()
                                            isPumpSetupActive = true
                                            isOtpSent = false
                                            Toast.makeText(context, "Phone verified automatically!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            completeLoginFlow(formattedMobile, rawMobile)
                                        }
                                    }.onFailure { e ->
                                        otpError = e.localizedMessage ?: "Verification failed"
                                    }
                                }
                            }
                        }

                        override fun onVerificationFailed(e: Exception) {
                            coroutineScope.launch(Dispatchers.Main) {
                                isLoading = false
                                val rawMessage = e.localizedMessage ?: "Unknown error"
                                val isRegionBlocked = rawMessage.contains("region", ignoreCase = true) || 
                                                     rawMessage.contains("policy", ignoreCase = true) || 
                                                     rawMessage.contains("blocked", ignoreCase = true)
                                val isBillingError = rawMessage.contains("billing", ignoreCase = true) || 
                                                     rawMessage.contains("quota", ignoreCase = true) || 
                                                     rawMessage.contains("upgrade", ignoreCase = true) ||
                                                     rawMessage.contains("pricing", ignoreCase = true)
                                
                                mobileNumberError = when {
                                    isRegionBlocked -> {
                                        "Firebase SMS Region Blocked:\n\n$rawMessage\n\n🛠️ HOW TO FIX:\n1. Go to Firebase Console.\n2. Navigate to Build > Authentication.\n3. Click the Settings tab at the top.\n4. Scroll down to 'SMS region policy'.\n5. Select 'Allow' or check 'Enable SMS region policy' and add your country code (e.g., India +91).\n6. Save changes and try again!"
                                    }
                                    isBillingError -> {
                                        "Firebase Billing / Quota Error:\n\n$rawMessage\n\n🛠️ HOW TO FIX (Choose ONE):\n\nOption A: Add Testing Phone Numbers (FREE & RECOMMENDED FOR DEV)\n1. Go to Firebase Console > Authentication > Sign-in method.\n2. Click 'Phone' to edit its settings.\n3. Expand 'Phone numbers for testing (optional)'.\n4. Add a test mobile number and verification code (e.g., +919999999999 and 123456).\n5. Click Save. This bypasses real SMS billing and is completely free!\n\nOption B: Upgrade to Blaze Plan\n1. In Firebase, click 'Upgrade' at the bottom left to change from Spark to Blaze plan (Pay-As-You-Go)."
                                    }
                                    else -> {
                                        "Firebase Error: $rawMessage\n\n💡 TIP: Please ensure 'Phone' is enabled as a sign-in provider in Firebase Console (Authentication > Sign-in method). Or select 'Simulated (Demo)' below to bypass SMS delivery!"
                                    }
                                }
                                Toast.makeText(context, "Firebase OTP Config/Billing Issue!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            } else {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    mobileNumberError = "Could not find Activity context for Firebase Phone Verification"
                }
            }
        }
    }

    // Handle authentication / password-based registration & login logic (No OTP)
    val handleAuth = {
        focusManager.clearFocus()
        usernameError = null
        ownerNameError = null
        passwordError = null
        confirmPasswordError = null
        mobileNumberError = null
        otpError = null

        val u = username.trim()
        val p = password.trim()
        val m = mobileNumber.trim()
        val cleanedMobile = m.filter { it.isDigit() }

        if (isOtpSent) {
            val entered = userEnteredOtp.trim()
            if (entered.isEmpty()) {
                otpError = "Please enter the verification code"
            } else {
                isLoading = true
                coroutineScope.launch {
                    val formattedMobile = com.example.database.FirestoreUserManager.formatMobileNumber(m)
                    if (otpMethodUsed == "Simulated (Demo Mode)") {
                        delay(1000)
                        if (entered == generatedOtp) {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                if (isSignUpMode) {
                                    val rawOwnerName = ownerName.trim()
                                    val firstThree = if (rawOwnerName.length >= 3) rawOwnerName.take(3) else rawOwnerName
                                    val lastSix = if (cleanedMobile.length >= 6) cleanedMobile.takeLast(6) else cleanedMobile
                                    val generatedPassword = firstThree + lastSix
                                    password = generatedPassword
                                    confirmPassword = generatedPassword
                                    pumpName = u
                                    isPumpSetupActive = true
                                    isOtpSent = false
                                    Toast.makeText(context, "Phone number verified (Demo Mode)!", Toast.LENGTH_SHORT).show()
                                } else {
                                    completeLoginFlow(formattedMobile, m)
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                otpError = "Invalid verification code"
                            }
                        }
                    } else {
                        val credential = com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId, entered)
                        com.example.database.FirebasePhoneAuthManager.signInWithCredential(credential) { result ->
                            coroutineScope.launch(Dispatchers.Main) {
                                result.onSuccess {
                                    isLoading = false
                                    if (isSignUpMode) {
                                        val rawOwnerName = ownerName.trim()
                                        val firstThree = if (rawOwnerName.length >= 3) rawOwnerName.take(3) else rawOwnerName
                                        val lastSix = if (cleanedMobile.length >= 6) cleanedMobile.takeLast(6) else cleanedMobile
                                        val generatedPassword = firstThree + lastSix
                                        password = generatedPassword
                                        confirmPassword = generatedPassword
                                        pumpName = u
                                        isPumpSetupActive = true
                                        isOtpSent = false
                                        Toast.makeText(context, "Phone number verified via Firebase!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        completeLoginFlow(formattedMobile, m)
                                    }
                                }.onFailure { e ->
                                    isLoading = false
                                    otpError = e.localizedMessage ?: "Verification failed"
                                }
                            }
                        }
                    }
                }
            }
        } else {
            var hasError = false
            if (isSignUpMode) {
                if (u.isEmpty()) {
                    usernameError = "Pump Name cannot be empty"
                    hasError = true
                } else if (u.length < 3) {
                    usernameError = "Pump Name must be at least 3 characters"
                    hasError = true
                }
                if (ownerName.trim().isEmpty()) {
                    ownerNameError = "Owner Name cannot be empty"
                    hasError = true
                } else if (ownerName.trim().length < 3) {
                    ownerNameError = "Owner Name must be at least 3 characters"
                    hasError = true
                }
            } else {
                if (!isOtpLoginMode && p.isEmpty()) {
                    passwordError = "Password cannot be empty"
                    hasError = true
                }
            }

            if (m.isEmpty()) {
                mobileNumberError = "Mobile number cannot be empty"
                hasError = true
            } else if (cleanedMobile.length < 10 || cleanedMobile.length > 15) {
                mobileNumberError = "Please enter a valid 10-15 digit mobile number"
                hasError = true
            }

            if (!hasError) {
                isLoading = true
                coroutineScope.launch {
                    val formattedMobile = com.example.database.FirestoreUserManager.formatMobileNumber(m)
                    if (isSignUpMode) {
                        val existingLogin = withContext(Dispatchers.IO) {
                            loginInfoDao.getLoginInfoByMobileNumber(formattedMobile) ?: loginInfoDao.getLoginInfoByMobileNumber(m)
                        }
                        if (existingLogin != null) {
                            withContext(Dispatchers.Main) {
                                mobileNumberError = "Mobile number is already registered"
                                isLoading = false
                            }
                        } else {
                            val checkResult = com.example.database.FirestoreUserManager.checkUserInFirestore(formattedMobile)
                            checkResult.onSuccess { userData ->
                                if (userData != null) {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        mobileNumberError = "Mobile number is already registered in cloud database"
                                        isLoading = false
                                    }
                                } else {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        dispatchOtpFlow(formattedMobile, m)
                                    }
                                }
                            }.onFailure { e ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    dispatchOtpFlow(formattedMobile, m)
                                }
                            }
                        }
                    } else {
                        if (isOtpLoginMode) {
                            dispatchOtpFlow(formattedMobile, m)
                        } else {
                            val info = withContext(Dispatchers.IO) {
                                loginInfoDao.getLoginInfoByMobileNumber(formattedMobile) ?: loginInfoDao.getLoginInfoByMobileNumber(m)
                            }
                            if (info != null) {
                                if (info.passwordHash == p) {
                                    withContext(Dispatchers.IO) {
                                        val pumpInfo = db.pumpInfoDao().getPumpInfoByMobile(info.mobileNumber)
                                        val pumpNameString = pumpInfo?.pumpName ?: info.username
                                        
                                        val existingNozzles = db.registeredNozzleDao().getNozzlesByPumpMobile(info.mobileNumber)
                                        if (existingNozzles.isEmpty() && pumpInfo != null) {
                                            val msList = pumpInfo.msNozzleLabels.split(",").filter { it.isNotBlank() }
                                            val hsdList = pumpInfo.hsdNozzleLabels.split(",").filter { it.isNotBlank() }
                                            val toInsert = mutableListOf<RegisteredNozzle>()
                                            msList.forEachIndexed { idx, label ->
                                                toInsert.add(RegisteredNozzle(mobileNumber = info.mobileNumber, nozzleType = "MS", label = label, nozzleIndex = idx))
                                            }
                                            hsdList.forEachIndexed { idx, label ->
                                                toInsert.add(RegisteredNozzle(mobileNumber = info.mobileNumber, nozzleType = "HSD", label = label, nozzleIndex = idx))
                                            }
                                            db.registeredNozzleDao().insertNozzles(toInsert)
                                        }
                                        
                                        val registeredList = db.registeredNozzleDao().getNozzlesByPumpMobile(info.mobileNumber)
                                        val finalMsLabels = if (registeredList.isNotEmpty()) {
                                            registeredList.filter { it.nozzleType == "MS" }.sortedBy { it.nozzleIndex }.map { it.label }
                                        } else {
                                            pumpInfo?.msNozzleLabels?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                                        }
                                        val finalHsdLabels = if (registeredList.isNotEmpty()) {
                                            registeredList.filter { it.nozzleType == "HSD" }.sortedBy { it.nozzleIndex }.map { it.label }
                                        } else {
                                            pumpInfo?.hsdNozzleLabels?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                                        }
                                        
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            Toast.makeText(
                                                context,
                                                "Logged in successfully! Registered phone: ${info.mobileNumber}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onLoginSuccess(info.username, pumpNameString, finalMsLabels, finalHsdLabels, info.mobileNumber)
                                        }
                                    }
                                } else {
                                    passwordError = "Incorrect password"
                                    isLoading = false
                                }
                            } else {
                                val checkResult = com.example.database.FirestoreUserManager.checkUserInFirestore(formattedMobile)
                                checkResult.onSuccess { userData ->
                                    if (userData != null) {
                                        val storedPasswordHash = userData["passwordHash"] as? String ?: ""
                                        val storedUsername = userData["username"] as? String ?: "D R Inamdar Petroleum"
                                        val storedOwnerName = userData["ownerName"] as? String ?: ""
                                        
                                        val firstThreeOfOwner = if (storedOwnerName.isNotEmpty()) {
                                            if (storedOwnerName.length >= 3) storedOwnerName.take(3) else storedOwnerName
                                        } else {
                                            if (storedUsername.length >= 3) storedUsername.take(3) else storedUsername
                                        }
                                        val firstTwoOfUsername = if (storedUsername.length >= 2) storedUsername.take(2) else storedUsername
                                        
                                        val expectedFallbackPasswordNew = firstThreeOfOwner + m.filter { it.isDigit() }.takeLast(6)
                                        val expectedFallbackPasswordOld = firstTwoOfUsername + m.filter { it.isDigit() }.takeLast(6)
                                        
                                        if (p == storedPasswordHash || (storedPasswordHash.isEmpty() && (p == expectedFallbackPasswordNew || p == expectedFallbackPasswordOld))) {
                                            withContext(Dispatchers.IO) {
                                                val loginInfo = LoginInfo(
                                                    mobileNumber = formattedMobile,
                                                    username = storedUsername,
                                                    passwordHash = p
                                                )
                                                loginInfoDao.insertLoginInfo(loginInfo)
                                                userDao.insertUser(User(username = formattedMobile, passwordHash = p))
                                                
                                                com.example.database.FirestoreSyncManager.checkAndDownloadLoginInfo(context, formattedMobile)
                                                
                                                val pumpInfo = db.pumpInfoDao().getPumpInfoByMobile(formattedMobile)
                                                val pumpNameString = pumpInfo?.pumpName ?: storedUsername
                                                
                                                val existingNozzles = db.registeredNozzleDao().getNozzlesByPumpMobile(formattedMobile)
                                                if (existingNozzles.isEmpty() && pumpInfo != null) {
                                                    val msList = pumpInfo.msNozzleLabels.split(",").filter { it.isNotBlank() }
                                                    val hsdList = pumpInfo.hsdNozzleLabels.split(",").filter { it.isNotBlank() }
                                                    val toInsert = mutableListOf<RegisteredNozzle>()
                                                    msList.forEachIndexed { idx, label ->
                                                        toInsert.add(RegisteredNozzle(mobileNumber = formattedMobile, nozzleType = "MS", label = label, nozzleIndex = idx))
                                                    }
                                                    hsdList.forEachIndexed { idx, label ->
                                                        toInsert.add(RegisteredNozzle(mobileNumber = formattedMobile, nozzleType = "HSD", label = label, nozzleIndex = idx))
                                                    }
                                                    db.registeredNozzleDao().insertNozzles(toInsert)
                                                }
                                                
                                                val registeredList = db.registeredNozzleDao().getNozzlesByPumpMobile(formattedMobile)
                                                val finalMsLabels = if (registeredList.isNotEmpty()) {
                                                    registeredList.filter { it.nozzleType == "MS" }.sortedBy { it.nozzleIndex }.map { it.label }
                                                } else {
                                                    pumpInfo?.msNozzleLabels?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                                                }
                                                val finalHsdLabels = if (registeredList.isNotEmpty()) {
                                                    registeredList.filter { it.nozzleType == "HSD" }.sortedBy { it.nozzleIndex }.map { it.label }
                                                } else {
                                                    pumpInfo?.hsdNozzleLabels?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                                                }
                                                
                                                withContext(Dispatchers.Main) {
                                                    isLoading = false
                                                    Toast.makeText(
                                                        context,
                                                        "Logged in successfully! Registered phone: $formattedMobile",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    onLoginSuccess(storedUsername, pumpNameString, finalMsLabels, finalHsdLabels, formattedMobile)
                                                }
                                            }
                                        } else {
                                            coroutineScope.launch(Dispatchers.Main) {
                                                isLoading = false
                                                passwordError = "Incorrect password"
                                            }
                                        }
                                    } else {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            isLoading = false
                                            mobileNumberError = "This mobile number is not registered. Please sign up."
                                        }
                                    }
                                }.onFailure { e ->
                                    Log.w("LoginScreen", "Firestore check failed (offline/network). Proceeding with local offline fallback.", e)
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val localUsername = if (u.isNotBlank()) u else "Pump Admin"
                                        val loginInfo = LoginInfo(
                                            mobileNumber = formattedMobile,
                                            username = localUsername,
                                            passwordHash = p
                                        )
                                        loginInfoDao.insertLoginInfo(loginInfo)
                                        userDao.insertUser(User(username = formattedMobile, passwordHash = p))
                                        
                                        // Create default local pump info if not exists
                                        var pumpInfo = db.pumpInfoDao().getPumpInfoByMobile(formattedMobile)
                                        if (pumpInfo == null) {
                                            pumpInfo = PumpInfo(
                                                mobileNumber = formattedMobile,
                                                pumpName = localUsername,
                                                numMsNozzles = 2,
                                                msNozzleLabels = "MS1,MS2",
                                                numHsdNozzles = 2,
                                                hsdNozzleLabels = "HSD1,HSD2",
                                                numMsTanks = 1,
                                                numHsdTanks = 1,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                            db.pumpInfoDao().insertPumpInfo(pumpInfo)
                                        }
                                        
                                        val existingNozzles = db.registeredNozzleDao().getNozzlesByPumpMobile(formattedMobile)
                                        if (existingNozzles.isEmpty()) {
                                            val toInsert = listOf(
                                                RegisteredNozzle(mobileNumber = formattedMobile, nozzleType = "MS", label = "MS1", nozzleIndex = 0),
                                                RegisteredNozzle(mobileNumber = formattedMobile, nozzleType = "MS", label = "MS2", nozzleIndex = 1),
                                                RegisteredNozzle(mobileNumber = formattedMobile, nozzleType = "HSD", label = "HSD1", nozzleIndex = 0),
                                                RegisteredNozzle(mobileNumber = formattedMobile, nozzleType = "HSD", label = "HSD2", nozzleIndex = 1)
                                            )
                                            db.registeredNozzleDao().insertNozzles(toInsert)
                                        }
                                        
                                        val registeredList = db.registeredNozzleDao().getNozzlesByPumpMobile(formattedMobile)
                                        val finalMsLabels = registeredList.filter { it.nozzleType == "MS" }.sortedBy { it.nozzleIndex }.map { it.label }
                                        val finalHsdLabels = registeredList.filter { it.nozzleType == "HSD" }.sortedBy { it.nozzleIndex }.map { it.label }
                                        
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            Toast.makeText(
                                                context,
                                                "Offline Mode: Logged in locally!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            onLoginSuccess(localUsername, pumpInfo.pumpName, finalMsLabels, finalHsdLabels, formattedMobile)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val handlePumpSetupComplete = {
        focusManager.clearFocus()
        pumpNameError = null
        val pName = pumpName.trim()
        val numMsT = numMsTanks.trim().toIntOrNull() ?: 1
        val numHsdT = numHsdTanks.trim().toIntOrNull() ?: 1
        val u = username.trim()
        val p = password.trim()
        val m = mobileNumber.trim()

        if (pName.isEmpty()) {
            pumpNameError = "Pump name cannot be empty"
        } else {
            isLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 1. Create standard user profile
                    val newUser = User(username = u, passwordHash = p)
                    userDao.insertUser(newUser)

                    // 2. Create login info record
                    val newLoginInfo = LoginInfo(mobileNumber = m, username = u, passwordHash = p)
                    loginInfoDao.insertLoginInfo(newLoginInfo)

                    // 3. Create pump info record
                    val msNozzleList = msLabels.take(msNozzlesCount).joinToString(",")
                    val hsdNozzleList = hsdLabels.take(hsdNozzlesCount).joinToString(",")
                    val newPumpInfo = PumpInfo(
                        mobileNumber = m,
                        pumpName = pName,
                        numMsNozzles = msNozzlesCount,
                        msNozzleLabels = msNozzleList,
                        numHsdNozzles = hsdNozzlesCount,
                        hsdNozzleLabels = hsdNozzleList,
                        numMsTanks = numMsT,
                        numHsdTanks = numHsdT
                    )
                    db.pumpInfoDao().insertPumpInfo(newPumpInfo)

                    // Insert administrator PhoneAccess record so registered number has ADMIN role
                    db.phoneAccessDao().insertAccess(PhoneAccess(phone = m, role = "ADMIN"))

                    // 4. Create and insert registered nozzles individually for the table
                    val msNozzlesToRegister = msLabels.take(msNozzlesCount).mapIndexed { idx, label ->
                        val readingStr = msInitialReadings.getOrNull(idx) ?: "0"
                        val readingVal = readingStr.toDoubleOrNull() ?: 0.0
                        RegisteredNozzle(mobileNumber = m, nozzleType = "MS", label = label, nozzleIndex = idx, initialReading = readingVal)
                    }
                    val hsdNozzlesToRegister = hsdLabels.take(hsdNozzlesCount).mapIndexed { idx, label ->
                        val readingStr = hsdInitialReadings.getOrNull(idx) ?: "0"
                        val readingVal = readingStr.toDoubleOrNull() ?: 0.0
                        RegisteredNozzle(mobileNumber = m, nozzleType = "HSD", label = label, nozzleIndex = idx, initialReading = readingVal)
                    }
                    db.registeredNozzleDao().insertNozzles(msNozzlesToRegister + hsdNozzlesToRegister)

                    // 5. Save the initial reading as initial closing readings in history table
                    val initialMsReadings = msNozzlesToRegister.map { rNozzle ->
                        MsNozzleReading(
                            nozzleLabel = rNozzle.label,
                            openingReading = rNozzle.initialReading,
                            closingReading = rNozzle.initialReading,
                            testing = 0.0,
                            caName = "Initial Setup",
                            phone = m,
                            udhar = 0.0,
                            kharch = 0.0,
                            udhariJama = 0.0,
                            msSales = 0.0,
                            hsdSales = 0.0,
                            timestamp = System.currentTimeMillis() - 1000,
                            date = "Initial Setup"
                        )
                    }
                    val initialHsdReadings = hsdNozzlesToRegister.map { rNozzle ->
                        HsdNozzleReading(
                            nozzleLabel = rNozzle.label,
                            openingReading = rNozzle.initialReading,
                            closingReading = rNozzle.initialReading,
                            testing = 0.0,
                            caName = "Initial Setup",
                            phone = m,
                            udhar = 0.0,
                            kharch = 0.0,
                            udhariJama = 0.0,
                            msSales = 0.0,
                            hsdSales = 0.0,
                            timestamp = System.currentTimeMillis() - 1000,
                            date = "Initial Setup"
                        )
                    }
                    db.msNozzleReadingDao().insertMsNozzleReadings(initialMsReadings)
                    db.hsdNozzleReadingDao().insertHsdNozzleReadings(initialHsdReadings)

                    // Save registered user to Firestore 'users' collection (Requirement 2)
                    try {
                        val formattedPhone = com.example.database.FirestoreUserManager.formatMobileNumber(m)
                        val saveResult = com.example.database.FirestoreUserManager.saveUserToFirestore(formattedPhone, u, ownerName, p)
                        if (saveResult.isSuccess) {
                            android.util.Log.d("PumpSetup", "Successfully registered user in Cloud Firestore 'users' collection.")
                        } else {
                            android.util.Log.e("PumpSetup", "Failed to register user in Cloud Firestore 'users' collection", saveResult.exceptionOrNull())
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PumpSetup", "Error writing user to Cloud Firestore", e)
                    }

                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(
                            context,
                            "Registration and Pump Setup successful! Please log in.",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Switch back to Login screen mode as requested
                        isSignUpMode = false
                        isPumpSetupActive = false
                        isOtpSent = false
                        userEnteredOtp = ""
                        generatedOtp = ""
                        confirmPassword = ""
                        mobileNumber = ""
                        ownerName = ""
                        
                        // Clear validation error flags
                        usernameError = null
                        ownerNameError = null
                        passwordError = null
                        confirmPasswordError = null
                        mobileNumberError = null
                        otpError = null
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(
                            context,
                            "Setup error: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated simulation alert box when OTP is active
            AnimatedVisibility(
                visible = isOtpSent,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = "SMS Notification icon",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (otpMethodUsed == "Simulated (Demo Mode)") "Simulated SMS Notification" else "OTP Dispatched Successfully",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (otpMethodUsed == "Simulated (Demo Mode)") {
                                    "Verification code for $mobileNumber: $generatedOtp"
                                } else {
                                    "A verification code has been dispatched via $otpMethodUsed to $mobileNumber. Please check your inbox."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Elegant Canvas-based abstract icon matching the pure petroleum motif
            // Fuel Nozzle Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalGasStation,
                    contentDescription = "Fuel Nozzle Logo",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App Name
            Text(
                text = "Pump Audit Daily",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.0.sp,
                    color = MaterialTheme.colorScheme.primary
                ),
                textAlign = TextAlign.Center
            )

            // Trademark
            Text(
                text = "Genovacre™",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.secondary
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            Text(
                text = "Fuel Audit & Shift Reconciliation",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Dynamic Form Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isPumpSetupActive) {
                        // --- PETROL PUMP CONFIGURATION CARD LAYOUT ---
                        Text(
                            text = LanguageManager.translate("Petrol Pump Setup", "पेट्रोल पंप सेटअप"),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("auth_form_title")
                        )

                        Text(
                            text = LanguageManager.translate(
                                "Configure tanks and nozzle labels for $pumpName to start auditing.",
                                "$pumpName के लिए टैंक और नोज़ल लेबल कॉन्फ़िगर करें।"
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        // Number of MS Tanks and HSD Tanks
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = numMsTanks,
                                onValueChange = { numMsTanks = it.filter { c -> c.isDigit() } },
                                label = { Text("MS Tanks") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = "MS tanks icon"
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("auth_num_ms_tanks_input")
                            )
                            OutlinedTextField(
                                value = numHsdTanks,
                                onValueChange = { numHsdTanks = it.filter { c -> c.isDigit() } },
                                label = { Text("HSD Tanks") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = "HSD tanks icon"
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("auth_num_hsd_tanks_input")
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // MS Nozzles Selector and Customize
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text("MS Nozzles Count", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Select up to 15", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (msNozzlesCount > 1) msNozzlesCount-- },
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease MS", modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        text = "$msNozzlesCount",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    IconButton(
                                        onClick = { if (msNozzlesCount < 15) msNozzlesCount++ },
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase MS", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Customize MS Nozzle Labels & Initial Readings:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    (0 until msNozzlesCount).forEach { index ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = msLabels.getOrElse(index) { "" },
                                                onValueChange = { newVal ->
                                                    val newList = msLabels.toMutableList()
                                                    if (index < newList.size) {
                                                        newList[index] = newVal
                                                    } else {
                                                        while (newList.size <= index) newList.add("MS Nozzle ${newList.size + 1}")
                                                        newList[index] = newVal
                                                    }
                                                    msLabels = newList
                                                },
                                                label = { Text("MS Noz ${index + 1} Label") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            OutlinedTextField(
                                                value = msInitialReadings.getOrElse(index) { "0" },
                                                onValueChange = { newVal ->
                                                    val newList = msInitialReadings.toMutableList()
                                                    val sanitized = newVal.filter { it.isDigit() || it == '.' }
                                                    if (index < newList.size) {
                                                        newList[index] = sanitized
                                                    } else {
                                                        while (newList.size <= index) newList.add("0")
                                                        newList[index] = sanitized
                                                    }
                                                    msInitialReadings = newList
                                                },
                                                label = { Text("Opening") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.width(110.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // HSD Nozzles Selector and Customize
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text("HSD Nozzles Count", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Select up to 15", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (hsdNozzlesCount > 1) hsdNozzlesCount-- },
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease HSD", modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        text = "$hsdNozzlesCount",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    IconButton(
                                        onClick = { if (hsdNozzlesCount < 15) hsdNozzlesCount++ },
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase HSD", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Customize HSD Nozzle Labels & Initial Readings:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    (0 until hsdNozzlesCount).forEach { index ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = hsdLabels.getOrElse(index) { "" },
                                                onValueChange = { newVal ->
                                                    val newList = hsdLabels.toMutableList()
                                                    if (index < newList.size) {
                                                        newList[index] = newVal
                                                    } else {
                                                        while (newList.size <= index) newList.add("HSD Nozzle ${newList.size + 1}")
                                                        newList[index] = newVal
                                                    }
                                                    hsdLabels = newList
                                                },
                                                label = { Text("HSD Noz ${index + 1} Label") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            OutlinedTextField(
                                                value = hsdInitialReadings.getOrElse(index) { "0" },
                                                onValueChange = { newVal ->
                                                    val newList = hsdInitialReadings.toMutableList()
                                                    val sanitized = newVal.filter { it.isDigit() || it == '.' }
                                                    if (index < newList.size) {
                                                        newList[index] = sanitized
                                                    } else {
                                                        while (newList.size <= index) newList.add("0")
                                                        newList[index] = sanitized
                                                    }
                                                    hsdInitialReadings = newList
                                                },
                                                label = { Text("Opening") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.width(110.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Complete Setup Button
                        Button(
                            onClick = { handlePumpSetupComplete() },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_complete_setup_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Complete Pump Setup & Register",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        // Go back link
                        Text(
                            text = "Back to Sign Up",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isPumpSetupActive = false
                                }
                                .padding(vertical = 4.dp)
                                .testTag("auth_back_to_otp_link")
                        )

                    } else if (isOtpSent) {
                        // --- OTP VERIFICATION CARD LAYOUT ---
                        Text(
                            text = "Verify Number",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("auth_form_title")
                        )

                        Text(
                            text = "We have sent a verification code to $mobileNumber. Please enter the OTP below.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        if (otpMethodUsed == "Simulated (Demo Mode)") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sms,
                                        contentDescription = "Simulated SMS icon",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "Simulated SMS Gateway",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Verification code for $mobileNumber is: $generatedOtp",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        // OTP Input Field
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = userEnteredOtp,
                                onValueChange = {
                                    userEnteredOtp = it
                                    otpError = null
                                },
                                label = { Text("4-Digit OTP") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Pin,
                                        contentDescription = "OTP code icon"
                                    )
                                },
                                isError = otpError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_otp_input"),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { handleAuth() }
                                )
                            )
                            if (otpError != null) {
                                Text(
                                    text = otpError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 4.dp).testTag("auth_otp_error")
                                )
                            }
                        }

                        // Verify button
                        Button(
                            onClick = { handleAuth() },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_verify_otp_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (isSignUpMode) "Verify & Complete Signup" else "Verify & Log In",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        // Go back link
                        Text(
                            text = if (isSignUpMode) "Change Mobile Number / Details" else "Change Mobile Number",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isOtpSent = false
                                    userEnteredOtp = ""
                                    otpError = null
                                }
                                .padding(vertical = 4.dp)
                                .testTag("auth_back_to_signup_details")
                        )

                    } else {
                        // --- STANDARD USERNAME/PASSWORD/MOBILE CARD LAYOUT ---
                        Text(
                            text = if (isSignUpMode) "Create Account" else "Sign In",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("auth_form_title")
                        )

                        // Pump Name Input
                        AnimatedVisibility(
                            visible = isSignUpMode,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = {
                                        username = it
                                        usernameError = null
                                    },
                                    label = { Text(LanguageManager.translate("Pump Name", "पंप का नाम")) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = "Pump Name field icon"
                                        )
                                    },
                                    isError = usernameError != null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_username_input"),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                    )
                                )
                                if (usernameError != null) {
                                    Text(
                                        text = usernameError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 4.dp).testTag("auth_username_error")
                                    )
                                }
                            }
                        }

                        // Owner Name Input
                        AnimatedVisibility(
                            visible = isSignUpMode,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = ownerName,
                                    onValueChange = {
                                        ownerName = it
                                        ownerNameError = null
                                    },
                                    label = { Text(LanguageManager.translate("Owner Name", "मालिक का नाम")) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Owner Name field icon"
                                        )
                                    },
                                    isError = ownerNameError != null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_owner_name_input"),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                    )
                                )
                                if (ownerNameError != null) {
                                    Text(
                                        text = ownerNameError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 4.dp).testTag("auth_owner_name_error")
                                    )
                                }
                            }
                        }

                        // Mobile Number Input (Always visible)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = mobileNumber,
                                onValueChange = {
                                    mobileNumber = it
                                    mobileNumberError = null
                                },
                                label = { Text(if (isSignUpMode) "Mobile Number" else "Mobile Number / Username") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "Mobile number field icon"
                                    )
                                },
                                isError = mobileNumberError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_mobile_input"),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = if (isSignUpMode) ImeAction.Next else ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                    onDone = { handleAuth() }
                                )
                            )
                            if (mobileNumberError != null) {
                                Text(
                                    text = mobileNumberError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 4.dp).testTag("auth_mobile_error")
                                )

                                if (mobileNumberError!!.contains("Firebase", ignoreCase = true) || mobileNumberError!!.contains("SMS", ignoreCase = true)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            otpMethodUsed = "Simulated (Demo Mode)"
                                            mobileNumberError = null
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("switch_to_simulated_mode_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sms,
                                            contentDescription = "Simulated SMS Icon",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Use Simulated (Demo) SMS Fallback")
                                    }
                                }

                                if (mobileNumberError!!.contains("Firestore", ignoreCase = true) || 
                                    mobileNumberError!!.contains("offline", ignoreCase = true) ||
                                    mobileNumberError!!.contains("client is offline", ignoreCase = true)
                                ) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { showAppCheckDiagnostics = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("app_check_diagnostics_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Diagnostics Icon",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Troubleshoot App Check / Offline Error")
                                    }
                                }
                            }

                            if (showAppCheckDiagnostics) {
                                val appCheckDebugToken = remember { MyApplication.getAppCheckDebugToken(context) }
                                AlertDialog(
                                    onDismissRequest = { showAppCheckDiagnostics = false },
                                    icon = { Icon(Icons.Default.BugReport, contentDescription = "Diagnostics", tint = MaterialTheme.colorScheme.primary) },
                                    title = { Text("Firebase Connection & App Check Fix") },
                                    text = {
                                        Column(
                                            modifier = Modifier.verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                "If your app cannot connect to Firebase or reports 'client offline', please verify the following backend settings:",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            
                                            Divider()
                                            
                                            Text(
                                                "1. Register your App Check Debug Token (Crucial)",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                "App Check is now fully implemented on the client! Because you have enforced App Check in the Firebase Console, you must register your debug token so Firestore accepts the connection:\n" +
                                                "• Open Firebase Console -> Build -> App Check -> Apps tab.\n" +
                                                "• Click the 3 vertical dots next to your Android App, select 'Manage debug tokens', and click 'Add debug token' to save it.\n" +
                                                "• Alternatively, you can temporarily disable enforcement under the APIs tab while debugging.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            if (appCheckDebugToken != null) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = MaterialTheme.shapes.small,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp)
                                                    ) {
                                                        Text(
                                                            "Your App Check Debug Token:",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        androidx.compose.foundation.text.selection.SelectionContainer {
                                                            Text(
                                                                appCheckDebugToken,
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                                        Button(
                                                            onClick = {
                                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(appCheckDebugToken))
                                                            },
                                                            modifier = Modifier.align(Alignment.End),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.secondary,
                                                                contentColor = MaterialTheme.colorScheme.onSecondary
                                                            )
                                                        ) {
                                                            Text("Copy to Clipboard")
                                                        }
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = MaterialTheme.shapes.small,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        "Debug token has not been generated or cached yet. Make sure you are in a Debug build and try triggering a sync.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(12.dp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            Text(
                                                "2. Configure Firestore Security Rules",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                "Ensure your Firestore Database rules allow the app to read and write:\n" +
                                                "• Open Firebase Console -> Firestore Database -> Rules tab.\n" +
                                                "• For development or open setup, write:\n" +
                                                "  allow read, write: if true;",
                                                style = MaterialTheme.typography.bodySmall
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                "3. Offline Fallback Mode",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                "If your network is down or Firebase is not configured, the application is designed to save all data locally in the local SQLite database. It will sync automatically as soon as connection is restored.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showAppCheckDiagnostics = false }) {
                                            Text("Close")
                                        }
                                    }
                                )
                            }
                        }

                        // Toggle Login Method (Password vs OTP)
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !isOtpLoginMode,
                                    onClick = { isOtpLoginMode = false },
                                    label = { Text("Password Login") },
                                    leadingIcon = if (!isOtpLoginMode) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Password mode selection",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    modifier = Modifier.weight(1f).testTag("login_password_tab")
                                )
                                FilterChip(
                                    selected = isOtpLoginMode,
                                    onClick = { isOtpLoginMode = true },
                                    label = { Text("OTP Login") },
                                    leadingIcon = if (isOtpLoginMode) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Sms,
                                                contentDescription = "OTP mode selection",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    modifier = Modifier.weight(1f).testTag("login_otp_tab")
                                )
                            }
                        }

                        // Password Input
                        AnimatedVisibility(
                            visible = !isSignUpMode && !isOtpLoginMode,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = {
                                        password = it
                                        passwordError = null
                                    },
                                    label = { Text("Password") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Password field icon"
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    isError = passwordError != null,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_password_input"),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                    )
                                )
                                if (passwordError != null) {
                                    Text(
                                        text = passwordError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 4.dp).testTag("auth_password_error")
                                    )
                                }
                            }
                        }

                        // Confirm Password (Sign up mode only)
                        AnimatedVisibility(
                            visible = false,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = {
                                        confirmPassword = it
                                        confirmPasswordError = null
                                    },
                                    label = { Text("Confirm Password") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.LockClock,
                                            contentDescription = "Confirm password field icon"
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                            Icon(
                                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle confirm password visibility"
                                            )
                                        }
                                    },
                                    isError = confirmPasswordError != null,
                                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_confirm_password_input"),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { handleAuth() }
                                    )
                                )
                                if (confirmPasswordError != null) {
                                    Text(
                                        text = confirmPasswordError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 4.dp).testTag("auth_confirm_password_error")
                                    )
                                }
                            }
                        }

                        // SMS Dispatch Gateway (Real Firebase Auth vs Simulated Demo Mode)
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "SMS Dispatch Gateway",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = otpMethodUsed == "Firebase Auth SMS",
                                        onClick = { otpMethodUsed = "Firebase Auth SMS" },
                                        label = { Text("Real Firebase SMS") },
                                        leadingIcon = if (otpMethodUsed == "Firebase Auth SMS") {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        modifier = Modifier.weight(1f).testTag("otp_method_firebase")
                                    )
                                    FilterChip(
                                        selected = otpMethodUsed == "Simulated (Demo Mode)",
                                        onClick = { otpMethodUsed = "Simulated (Demo Mode)" },
                                        label = { Text("Simulated (Demo)") },
                                        leadingIcon = if (otpMethodUsed == "Simulated (Demo Mode)") {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        modifier = Modifier.weight(1f).testTag("otp_method_simulated")
                                    )
                                }
                            }
                        }

                        // Primary Submit Button
                        Button(
                            onClick = { handleAuth() },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_submit_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (isSignUpMode) "Register" else if (isOtpLoginMode) "Send OTP via SMS" else "Log In",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        // Toggle mode text
                        Text(
                            text = if (isSignUpMode) "Already have an account? Log In" else "Don't have an account? Sign Up",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isSignUpMode = !isSignUpMode
                                    resetFormStates()
                                }
                                .padding(vertical = 4.dp)
                                .testTag("auth_toggle_mode_link")
                        )
                    }
                }
            }

        }

        // Top Toolbar (Theme toggle) - Positioned on top of Column (higher z-index)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { onThemeChange(!isDarkTheme) },
                modifier = Modifier.testTag("auth_theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseExplorerDialog(
    onDismiss: () -> Unit,
    userDao: com.example.database.UserDao,
    loginInfoDao: com.example.database.LoginInfoDao,
    savedAuditDao: com.example.database.SavedAuditDao,
    pumpInfoDao: com.example.database.PumpInfoDao,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    var users by remember { mutableStateOf(emptyList<User>()) }
    var loginInfos by remember { mutableStateOf(emptyList<LoginInfo>()) }
    var savedAudits by remember { mutableStateOf(emptyList<com.example.database.SavedAudit>()) }
    var pumpInfos by remember { mutableStateOf(emptyList<com.example.database.PumpInfo>()) }
    var isLoading by remember { mutableStateOf(false) }

    // State for confirmation dialogs
    var showDeleteConfirmAll by remember { mutableStateOf<Int?>(null) } // null or tab index

    fun reloadAll() {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val u = userDao.getAllUsers()
                val l = loginInfoDao.getAllLoginInfo()
                val p = pumpInfoDao.getAllPumpInfo()
                val a = try {
                    savedAuditDao.getAllAudits().first()
                } catch(e: Exception) {
                    emptyList()
                }
                withContext(Dispatchers.Main) {
                    users = u
                    loginInfos = l
                    pumpInfos = p
                    savedAudits = a
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadAll()
    }

    // Fullscreen Dialog matching M3
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin DB icon",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "System Admin Console",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Inspect and manage app SQLite tables",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close admin panel"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { reloadAll() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh data"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Clear current table button
                        OutlinedButton(
                            onClick = {
                                showDeleteConfirmAll = selectedTab
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Clear icon", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Table", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter table rows...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Tabs for Tables
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; searchQuery = "" },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Person, contentDescription = "Users tab icon", modifier = Modifier.size(16.dp))
                                Text("Users (${users.size})")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; searchQuery = "" },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Phone, contentDescription = "Login info tab icon", modifier = Modifier.size(16.dp))
                                Text("Login Info (${loginInfos.size})")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; searchQuery = "" },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Assessment, contentDescription = "Saved audits tab icon", modifier = Modifier.size(16.dp))
                                Text("Audits (${savedAudits.size})")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3; searchQuery = "" },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Place, contentDescription = "Pump info tab icon", modifier = Modifier.size(16.dp))
                                Text("Pumps (${pumpInfos.size})")
                            }
                        }
                    )
                }

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (selectedTab) {
                        0 -> {
                            val filteredUsers = users.filter { 
                                it.username.contains(searchQuery, ignoreCase = true) ||
                                it.passwordHash.contains(searchQuery, ignoreCase = true)
                            }
                            if (filteredUsers.isEmpty()) {
                                EmptyPlaceholder("No users found")
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredUsers.size) { index ->
                                        val u = filteredUsers[index]
                                        UserRow(user = u, onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                userDao.deleteUserByUsername(u.username)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        })
                                    }
                                }
                            }
                        }
                        1 -> {
                            val filteredLoginInfos = loginInfos.filter {
                                it.username.contains(searchQuery, ignoreCase = true) ||
                                it.mobileNumber.contains(searchQuery, ignoreCase = true)
                            }
                            if (filteredLoginInfos.isEmpty()) {
                                EmptyPlaceholder("No mobile login records found")
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredLoginInfos.size) { index ->
                                        val info = filteredLoginInfos[index]
                                        LoginInfoRow(info = info, onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                loginInfoDao.deleteLoginInfoByMobile(info.mobileNumber)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        })
                                    }
                                }
                            }
                        }
                        2 -> {
                            val filteredAudits = savedAudits.filter {
                                it.caName.contains(searchQuery, ignoreCase = true) ||
                                it.meterNo.contains(searchQuery, ignoreCase = true) ||
                                it.auditType.contains(searchQuery, ignoreCase = true) ||
                                it.summaryText.contains(searchQuery, ignoreCase = true)
                            }
                            if (filteredAudits.isEmpty()) {
                                EmptyPlaceholder("No saved audit records found")
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredAudits.size) { index ->
                                        val audit = filteredAudits[index]
                                        SavedAuditRow(audit = audit, onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                savedAuditDao.deleteAuditById(audit.id)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        })
                                    }
                                }
                            }
                        }
                        3 -> {
                            val filteredPumps = pumpInfos.filter {
                                it.pumpName.contains(searchQuery, ignoreCase = true) ||
                                it.mobileNumber.contains(searchQuery, ignoreCase = true) ||
                                it.msNozzleLabels.contains(searchQuery, ignoreCase = true) ||
                                it.hsdNozzleLabels.contains(searchQuery, ignoreCase = true)
                            }
                            if (filteredPumps.isEmpty()) {
                                EmptyPlaceholder("No petrol pump setup records found")
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredPumps.size) { index ->
                                        val pump = filteredPumps[index]
                                        PumpInfoRow(pump = pump, onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                pumpInfoDao.deletePumpInfoByMobile(pump.mobileNumber)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Drop Table Confirmation
        if (showDeleteConfirmAll != null) {
            val tabIdx = showDeleteConfirmAll!!
            val tableName = when (tabIdx) {
                0 -> "USERS"
                1 -> "LOGIN INFO"
                2 -> "SAVED AUDITS"
                else -> "PUMP INFO"
            }
            AlertDialog(
                onDismissRequest = { showDeleteConfirmAll = null },
                title = { Text("Reset Table: $tableName", fontWeight = FontWeight.Bold) },
                text = { Text("Are you absolutely sure you want to delete all rows from the $tableName table? This action is irreversible.") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                when (tabIdx) {
                                    0 -> userDao.clearAllUsers()
                                    1 -> loginInfoDao.clearAllLoginInfo()
                                    2 -> savedAuditDao.clearAllAudits()
                                    3 -> pumpInfoDao.clearAllPumpInfo()
                                }
                                withContext(Dispatchers.Main) {
                                    showDeleteConfirmAll = null
                                    reloadAll()
                                    Toast.makeText(context, "$tableName table cleared!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete All", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmAll = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun UserRow(user: User, onDelete: () -> Unit) {
    var showPassword by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "User icon", tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(text = user.username, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (showPassword) "Password: ${user.passwordHash}" else "Password: ••••••••",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { showPassword = !showPassword }
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete user", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun LoginInfoRow(info: LoginInfo, onDelete: () -> Unit) {
    val dateStr = remember(info.verifiedAt) {
        java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(info.verifiedAt))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Phone icon", tint = MaterialTheme.colorScheme.secondary)
                }
                Column {
                    Text(text = info.mobileNumber, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Linked to: @${info.username}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Verified on: $dateStr",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete login info", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SavedAuditRow(audit: com.example.database.SavedAudit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = "Audit icon", tint = MaterialTheme.colorScheme.tertiary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = audit.caName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        SuggestionChip(
                            onClick = {},
                            label = { Text(audit.auditType, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    Text(
                        text = "Date: ${audit.date} | Meter: ${audit.meterNo}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = audit.summaryText,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete audit", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun PumpInfoRow(pump: com.example.database.PumpInfo, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Place, contentDescription = "Pump icon", tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(text = pump.pumpName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Mobile (Primary Key): ${pump.mobileNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "MS Tanks: ${pump.numMsTanks} | HSD Tanks: ${pump.numHsdTanks}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = "MS Nozzles: ${pump.msNozzleLabels}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "HSD Nozzles: ${pump.hsdNozzleLabels}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete pump info", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun EmptyPlaceholder(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "No data available",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)),
            textAlign = TextAlign.Center
        )
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

