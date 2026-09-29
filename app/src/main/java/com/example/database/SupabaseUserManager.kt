package com.example.database

import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class RegistrationParams(
    val p_user_data: SupabaseUser,
    val p_pump_data: PumpInfo,
    val p_nozzles: List<RegisteredNozzle>
)

/**
 * Manager for Supabase operations, mirroring FirestoreUserManager.
 * This allows the app to transition from Firebase to Supabase.
 */
object SupabaseUserManager {
    private const val TAG = "SupabaseUserManager"
    private val client = SupabaseClient.client

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
     * Registers a new owner and all associated data in a single atomic transaction,
     * with a robust direct table upsert fallback if the RPC function is missing or fails.
     */
    suspend fun registerOwnerAtomic(params: RegistrationParams): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting atomic registration for: ${params.p_user_data.mobileNumber}")
            
            var savedUser: SupabaseUser? = null
            var rpcError: Exception? = null

            // Try RPC first (bypasses RLS via SECURITY DEFINER)
            try {
                val response = client.postgrest.rpc("register_new_owner_atomic", params)
                Log.d(TAG, "RPC Response Body: ${response.data}")

                if (response.data.isNotBlank() && response.data != "null") {
                    savedUser = try {
                        response.decodeSingle<SupabaseUser>()
                    } catch (e: Exception) {
                        Log.w(TAG, "decodeSingle failed, attempting direct decode")
                        response.decodeAs<SupabaseUser>()
                    }
                }
            } catch (rpcEx: Exception) {
                rpcError = rpcEx
                Log.w(TAG, "RPC register_new_owner_atomic failed or not found: ${rpcEx.message}")
            }

            // If RPC failed or didn't return a savedUser, try direct table upserts
            if (savedUser == null) {
                try {
                    val user = params.p_user_data
                    val pumpInfo = params.p_pump_data
                    val nozzles = params.p_nozzles
                    val mobile = user.mobileNumber

                    val userResponse = client.postgrest["users"].upsert(user, onConflict = "mobile_number") {
                        select()
                    }
                    val freshUser = userResponse.decodeSingleOrNull<SupabaseUser>() ?: user

                    // Upsert Pump Info
                    client.postgrest["pumps"].upsert(pumpInfo)

                    // Upsert Membership
                    val membership = SupabaseMembership(
                        staffPhone = mobile,
                        adminPhone = mobile,
                        pumpName = pumpInfo.pumpName,
                        username = user.username ?: "Admin",
                        role = "ADMIN",
                        accountId = freshUser.id
                    )
                    client.postgrest["memberships"].upsert(membership, onConflict = "staff_phone,admin_phone")

                    // Save Nozzles into registered_nozzles table
                    if (nozzles.isNotEmpty()) {
                        Log.d(TAG, "Saving ${nozzles.size} nozzles for $mobile")
                        try {
                            client.postgrest["registered_nozzles"].delete {
                                filter {
                                    eq("mobile_number", mobile)
                                }
                            }
                            client.postgrest["registered_nozzles"].insert(nozzles)
                        } catch (nozEx: Exception) {
                            Log.w(TAG, "Delete/Insert nozzles failed, trying upsert fallback: ${nozEx.message}")
                            client.postgrest["registered_nozzles"].upsert(nozzles)
                        }
                    }
                    Result.success(freshUser)
                } catch (directEx: Exception) {
                    Log.e(TAG, "Direct table upsert failed: ${directEx.message}", directEx)
                    if (directEx.message?.contains("row level security", ignoreCase = true) == true ||
                        rpcError?.message?.contains("function", ignoreCase = true) == true) {
                        throw Exception("Database Setup Required: Please execute schema.sql in your Supabase SQL Editor. This sets up the registration function and disables Row Level Security (RLS) so tables can be written to.")
                    } else {
                        throw directEx
                    }
                }
            } else {
                Result.success(savedUser)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Atomic registration failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Checks if a user exists in the 'users' table.
     */
    suspend fun checkUserInSupabase(mobileNumber: String): Result<SupabaseUser?> = withContext(Dispatchers.IO) {
        try {
            val formattedMobile = formatMobileNumber(mobileNumber)
            Log.d(TAG, "Checking Supabase 'users' table for: $formattedMobile")
            
            val response = client.postgrest["users"].select(columns = Columns.ALL) {
                filter {
                    eq("mobile_number", formattedMobile)
                }
            }
            
            val data = response.decodeSingleOrNull<SupabaseUser>()
            Log.d(TAG, "Supabase check result: $data")
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking user in Supabase", e)
            Result.failure(e)
        }
    }

    /**
     * Saves or updates a user in the 'users' table and creates an admin membership.
     * Returns the created/updated user with ID.
     */
    suspend fun saveUserToSupabase(
        mobileNumber: String,
        ownerName: String,
        pumpName: String,
        passwordHash: String = ""
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        try {
            val formattedMobile = formatMobileNumber(mobileNumber)
            Log.d(TAG, "Saving user to Supabase 'users' table: $formattedMobile")
            
            val user = SupabaseUser(
                mobileNumber = formattedMobile,
                username = ownerName,
                ownerName = ownerName,
                pumpName = pumpName,
                role = "ADMIN",
                passwordHash = passwordHash
            )
            
            val response = client.postgrest["users"].upsert(user, onConflict = "mobile_number") {
                select()
            }
            val savedUser = response.decodeSingle<SupabaseUser>()

            // Membership
            val membership = SupabaseMembership(
                staffPhone = formattedMobile,
                adminPhone = formattedMobile,
                pumpName = pumpName,
                username = ownerName,
                role = "ADMIN",
                accountId = savedUser.id
            )
            client.postgrest["memberships"].upsert(membership)
            
            Log.d(TAG, "Supabase registration complete. ID: ${savedUser.id}")
            Result.success(savedUser)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user to Supabase", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves all memberships for a mobile number.
     */
    suspend fun getUserMemberships(mobileNumber: String): Result<List<SupabaseMembership>> = withContext(Dispatchers.IO) {
        try {
            val formattedMobile = formatMobileNumber(mobileNumber)
            val response = client.postgrest["memberships"].select(columns = Columns.ALL) {
                filter {
                    eq("staff_phone", formattedMobile)
                }
            }
            val memberships = response.decodeList<SupabaseMembership>()
            Result.success(memberships)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting memberships from Supabase", e)
            Result.failure(e)
        }
    }

    /**
     * Saves daily sales data to Supabase.
     */
    suspend fun saveDailySales(data: DailyPumpData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["daily_sales"].insert(data)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving daily sales to Supabase", e)
            Result.failure(e)
        }
    }

    /**
     * Saves TT receipt entry to Supabase.
     */
    suspend fun saveTtReceipt(data: TtReceiptEntry): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["tt_receipts"].insert(data)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving TT receipt to Supabase", e)
            Result.failure(e)
        }
    }

    /**
     * Updates pump configuration in Supabase.
     */
    suspend fun updatePumpInfo(data: PumpInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["pumps"].upsert(data)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating pump info in Supabase", e)
            Result.failure(e)
        }
    }
}
