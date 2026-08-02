package com.example.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "phone_access")
data class PhoneAccess(
    @PrimaryKey val phone: String,
    val role: String, // "MANAGER", "CA", or "BOTH"
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface PhoneAccessDao {
    @Query("SELECT * FROM phone_access WHERE phone = :phone LIMIT 1")
    suspend fun getAccessByPhone(phone: String): PhoneAccess?

    @Query("SELECT * FROM phone_access")
    suspend fun getAllAccess(): List<PhoneAccess>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccess(phoneAccess: PhoneAccess)

    @Query("DELETE FROM phone_access WHERE phone = :phone")
    suspend fun deleteAccessByPhone(phone: String)

    @Query("DELETE FROM phone_access")
    suspend fun clearAllAccess()
}
