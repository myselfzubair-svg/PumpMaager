package com.example.database

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FirestoreUserManager {
    private const val TAG = "FirestoreUserManager"
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /**
     * Standardizes a mobile number to include country code (default to +91 for India if 10 digits)
     * matches the logic in SmsOtpManager
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
     * Checks if a user's phone number is registered inside the "users" collection in Cloud Firestore.
     * Returns a Result containing the user document map if found, or null if not found.
     */
    suspend fun checkUserInFirestore(mobileNumber: String): Result<Map<String, Any>?> = withContext(Dispatchers.IO) {
        try {
            val formattedMobile = formatMobileNumber(mobileNumber)
            Log.d(TAG, "Checking users collection for document ID: $formattedMobile")
            
            val docRef = firestore.collection("users").document(formattedMobile)
            val task = docRef.get()
            val snapshot = Tasks.await(task)
            
            if (snapshot.exists()) {
                val data = snapshot.data
                Log.d(TAG, "User document found in 'users' collection: $data")
                Result.success(data)
            } else {
                Log.d(TAG, "User document NOT found in 'users' collection.")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking user in Firestore 'users' collection", e)
            Result.failure(e)
        }
    }

    /**
     * Creates or updates a user document inside the "users" collection in Cloud Firestore.
     */
    suspend fun saveUserToFirestore(mobileNumber: String, username: String, ownerName: String = "", passwordHash: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val formattedMobile = formatMobileNumber(mobileNumber)
            Log.d(TAG, "Saving user registration to 'users' collection for document ID: $formattedMobile")
            
            val docRef = firestore.collection("users").document(formattedMobile)
            val data = hashMapOf(
                "mobileNumber" to formattedMobile,
                "username" to username,
                "ownerName" to ownerName,
                "role" to "ADMIN",
                "passwordHash" to passwordHash,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )
            
            Tasks.await(docRef.set(data, SetOptions.merge()))
            Log.d(TAG, "Successfully saved user to 'users' collection")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user to Firestore 'users' collection", e)
            Result.failure(e)
        }
    }
}
