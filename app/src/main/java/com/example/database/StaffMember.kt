package com.example.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "staff_members", primaryKeys = ["phone", "ownerAdminPhone"])
@Serializable
data class StaffMember(
    val phone: String,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String, // The Admin who created this staff member
    val name: String,
    @SerialName("password_hash") val passwordHash: String = "",
    val role: String,
    @SerialName("account_id") val accountId: String? = null
)

@Dao
interface StaffMemberDao {
    @Query("SELECT * FROM staff_members WHERE phone = :phone AND ownerAdminPhone = :adminPhone LIMIT 1")
    suspend fun getStaffMemberByPhone(phone: String, adminPhone: String): StaffMember?

    @Query("SELECT * FROM staff_members WHERE phone = :phone LIMIT 1")
    suspend fun getStaffMemberByPhoneOnly(phone: String): StaffMember?

    @Query("SELECT * FROM staff_members WHERE ownerAdminPhone = :adminPhone")
    suspend fun getStaffByAdmin(adminPhone: String): List<StaffMember>

    @Query("SELECT * FROM staff_members")
    suspend fun getAllStaffMembers(): List<StaffMember>

    @Query("DELETE FROM staff_members WHERE ownerAdminPhone = :adminPhone")
    suspend fun clearStaffByAdmin(adminPhone: String)

    @Query("DELETE FROM staff_members")
    suspend fun clearAllStaffMembers()

    @Query("DELETE FROM staff_members WHERE phone = :phone AND ownerAdminPhone = :adminPhone")
    suspend fun deleteStaffMember(phone: String, adminPhone: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaffMember(staffMember: StaffMember)
}
