package com.example.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "staff")
data class Staff(
    @PrimaryKey val phone: String,
    val name: String,
    val passwordHash: String,
    val role: String
)

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff WHERE phone = :phone LIMIT 1")
    suspend fun getStaffByPhone(phone: String): Staff?

    @Query("SELECT * FROM staff")
    suspend fun getAllStaff(): List<Staff>

    @Query("DELETE FROM staff")
    suspend fun clearAllStaff()

    @Query("DELETE FROM staff WHERE phone = :phone")
    suspend fun deleteStaffByPhone(phone: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: Staff)
}
