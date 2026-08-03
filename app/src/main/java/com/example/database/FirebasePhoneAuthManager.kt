package com.example.database

import android.app.Activity
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

object FirebasePhoneAuthManager {
    private const val TAG = "FirebasePhoneAuth"
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    interface VerificationCallbacks {
        fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken)
        fun onVerificationCompleted(credential: PhoneAuthCredential)
        fun onVerificationFailed(e: Exception)
    }

    /**
     * Starts the Phone Number Verification flow using Firebase Authentication.
     */
    fun startPhoneNumberVerification(
        activity: Activity,
        phoneNumber: String,
        callbacks: VerificationCallbacks
    ) {
        val formattedNumber = FirestoreUserManager.formatMobileNumber(phoneNumber)
        Log.d(TAG, "Starting phone verification for: $formattedNumber")

        val firebaseCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "onVerificationCompleted: $credential")
                callbacks.onVerificationCompleted(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "onVerificationFailed: code=${e.message}", e)
                Log.e(TAG, "Check if SHA-1/SHA-256 fingerprints are added to Firebase Console.")
                Log.e(TAG, "Check if Phone Auth is enabled in Firebase Console.")
                callbacks.onVerificationFailed(e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "onCodeSent: verificationId=$verificationId")
                callbacks.onCodeSent(verificationId, token)
            }
        }

        try {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(formattedNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(firebaseCallbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start phone verification", e)
            callbacks.onVerificationFailed(e)
        }
    }

    /**
     * Signs in with a PhoneAuthCredential.
     * Invokes onComplete with success containing the verified phone number, or failure containing the exception.
     */
    fun signInWithCredential(
        credential: PhoneAuthCredential,
        onComplete: (Result<String>) -> Unit
    ) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = task.result?.user
                    val phone = firebaseUser?.phoneNumber ?: ""
                    Log.d(TAG, "signInWithCredential successful for: $phone")
                    onComplete(Result.success(phone))
                } else {
                    val ex = task.exception ?: Exception("Authentication failed")
                    Log.e(TAG, "signInWithCredential failed", ex)
                    onComplete(Result.failure(ex))
                }
            }
    }
}
