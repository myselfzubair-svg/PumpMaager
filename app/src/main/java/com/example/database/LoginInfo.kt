package com.example.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "login_info")
data class LoginInfo(
    @PrimaryKey val mobileNumber: String,
    val username: String,
    val passwordHash: String = "",
    val verifiedAt: Long = System.currentTimeMillis()
)

@Dao
interface LoginInfoDao {
    @Query("SELECT * FROM login_info WHERE username = :username LIMIT 1")
    suspend fun getLoginInfoByUsername(username: String): LoginInfo?

    @Query("SELECT * FROM login_info WHERE mobileNumber = :mobileNumber LIMIT 1")
    suspend fun getLoginInfoByMobileNumber(mobileNumber: String): LoginInfo?

    @Query("SELECT * FROM login_info")
    suspend fun getAllLoginInfo(): List<LoginInfo>

    @Query("DELETE FROM login_info")
    suspend fun clearAllLoginInfo()

    @Query("DELETE FROM login_info WHERE mobileNumber = :mobileNumber")
    suspend fun deleteLoginInfoByMobile(mobileNumber: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoginInfo(loginInfo: LoginInfo)
}
