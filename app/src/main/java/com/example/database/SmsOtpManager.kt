package com.example.database

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object SmsOtpManager {
    private const val TAG = "SmsOtpManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Standardizes a mobile number to include country code (default to +91 for India if 10 digits)
     */
    fun formatMobileNumber(mobile: String): String {
        val clean = mobile.filter { it.isDigit() }
        return when {
            clean.length == 10 -> "+91$clean"
            clean.length > 10 && !mobile.startsWith("+") -> "+$clean"
            else -> mobile
        }
    }

    /**
     * Clean number to digits only (used for domestic SMS APIs)
     */
    fun cleanToDigits(mobile: String): String {
        return mobile.filter { it.isDigit() }
    }

    /**
     * Sends OTP code to the recipient mobile number using the best available configured method.
     * Returns a Result containing a status description.
     */
    suspend fun sendOtp(context: Context, mobileNumber: String, otpCode: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanMobile = mobileNumber.trim()
        if (cleanMobile.isEmpty()) {
            return@withContext Result.failure(Exception("Mobile number is empty"))
        }

        val message = "Your fuel pump app login OTP is $otpCode. Do not share this code with anyone."

        // 1. Check Twilio Configuration
        val twilioSid = try { BuildConfig.TWILIO_ACCOUNT_SID } catch (e: Throwable) { "" }
        val twilioToken = try { BuildConfig.TWILIO_AUTH_TOKEN } catch (e: Throwable) { "" }
        val twilioSender = try { BuildConfig.TWILIO_SENDER_NUMBER } catch (e: Throwable) { "" }

        val isTwilioConfigured = twilioSid.isNotBlank() && 
                !twilioSid.contains("PLACEHOLDER", ignoreCase = true) &&
                twilioToken.isNotBlank() && 
                !twilioToken.contains("PLACEHOLDER", ignoreCase = true)

        if (isTwilioConfigured) {
            Log.d(TAG, "Attempting to send OTP via Twilio...")
            val twilioResult = sendViaTwilio(twilioSid, twilioToken, twilioSender, formatMobileNumber(cleanMobile), message)
            if (twilioResult.isSuccess) {
                return@withContext Result.success("Sent via Twilio Cloud Gateway")
            } else {
                Log.e(TAG, "Twilio failed: ${twilioResult.exceptionOrNull()?.message}")
            }
        }

        // 2. Check Fast2SMS Configuration
        val fast2smsKey = try { BuildConfig.FAST2SMS_API_KEY } catch (e: Throwable) { "" }
        val isFast2SmsConfigured = fast2smsKey.isNotBlank() && 
                !fast2smsKey.contains("PLACEHOLDER", ignoreCase = true)

        if (isFast2SmsConfigured) {
            Log.d(TAG, "Attempting to send OTP via Fast2SMS...")
            val fastResult = sendViaFast2Sms(fast2smsKey, cleanToDigits(cleanMobile), otpCode)
            if (fastResult.isSuccess) {
                return@withContext Result.success("Sent via Fast2SMS Cloud Gateway")
            } else {
                Log.e(TAG, "Fast2SMS failed: ${fastResult.exceptionOrNull()?.message}")
            }
        }

        // 3. Fallback to Device SIM card (SmsManager) if permissions are granted
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context, 
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSmsPermission) {
            Log.d(TAG, "Attempting to send OTP via Device SIM...")
            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                
                smsManager.sendTextMessage(cleanMobile, null, message, null, null)
                Log.d(TAG, "Device SIM SMS sent successfully")
                return@withContext Result.success("Sent via Device SIM Card")
            } catch (e: Exception) {
                Log.e(TAG, "Device SIM SMS failed: ${e.localizedMessage}")
            }
        }

        // 4. Return Simulation Status with clear instruction
        Log.d(TAG, "No real SMS configuration found or permission granted. Running in simulated mode.")
        return@withContext Result.success("Simulated (Demo Mode)")
    }

    private fun sendViaTwilio(
        sid: String, 
        token: String, 
        from: String, 
        to: String, 
        body: String
    ): Result<Unit> {
        return try {
            val url = "https://api.twilio.com/2010-04-01/Accounts/$sid/Messages.json"
            val authHeader = "Basic " + Base64.encodeToString("$sid:$token".toByteArray(), Base64.NO_WRAP)

            val formBody = FormBody.Builder()
                .add("To", to)
                .add("From", from)
                .add("Body", body)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", authHeader)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val respBody = response.body?.string() ?: ""
                    Result.failure(Exception("HTTP ${response.code}: $respBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sendViaFast2Sms(
        apiKey: String, 
        numbers: String, 
        otpCode: String
    ): Result<Unit> {
        return try {
            val url = "https://www.fast2sms.com/dev/bulkV2"
            
            // Format of Indian mobile number
            val finalNumbers = if (numbers.length > 10) numbers.takeLast(10) else numbers

            val json = """
                {
                    "route": "otp",
                    "variables_values": "$otpCode",
                    "numbers": "$finalNumbers"
                }
            """.trimIndent()

            val body = json.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .addHeader("authorization", apiKey)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val respBody = response.body?.string() ?: ""
                    Result.failure(Exception("HTTP ${response.code}: $respBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
