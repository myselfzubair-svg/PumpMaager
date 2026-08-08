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
     * Creates or updates a user document inside the "users" collection and registers the membership.
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

            // Also register as an Admin membership for themselves
            val membershipRef = firestore.collection("memberships").document(formattedMobile)
                .collection("accounts").document("${formattedMobile}_ADMIN")
            val membershipData = hashMapOf(
                "adminPhone" to formattedMobile,
                "pumpName" to username,
                "username" to username,
                "role" to "ADMIN"
            )
            Tasks.await(membershipRef.set(membershipData, SetOptions.merge()))

            Log.d(TAG, "Successfully saved user and registered membership")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user to Firestore 'users' collection", e)
            Result.failure(e)
        }
    }

    /**
     * Records a membership for a staff member.
     */
    suspend fun addStaffMembership(staffPhone: String, adminPhone: String, staffName: String, pumpName: String, role: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val formattedStaff = formatMobileNumber(staffPhone)
            val formattedAdmin = formatMobileNumber(adminPhone)
            val membershipRef = firestore.collection("memberships").document(formattedStaff)
                .collection("accounts").document("${formattedAdmin}_${role}")
            val data = hashMapOf(
                "adminPhone" to formattedAdmin,
                "pumpName" to pumpName,
                "username" to staffName,
                "role" to role
            )
            Tasks.await(membershipRef.set(data, SetOptions.merge()))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieves all accounts associated with a mobile number.
     */
    suspend fun getUserMemberships(mobileNumber: String): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try {
            val formattedMobile = formatMobileNumber(mobileNumber)
            val colRef = firestore.collection("memberships").document(formattedMobile).collection("accounts")
            val task = colRef.get()
            val snapshot = Tasks.await(task)
            val memberships = snapshot.documents.map { it.data ?: emptyMap() }
            Result.success(memberships)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
