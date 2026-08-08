package com.example.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "account_owners")
data class AccountOwner(
    @PrimaryKey val mobileNumber: String,
    val username: String,
    val passwordHash: String = "",
    val verifiedAt: Long = System.currentTimeMillis()
)

@Dao
interface AccountOwnerDao {
    @Query("SELECT * FROM account_owners WHERE username = :username LIMIT 1")
    suspend fun getAccountByUsername(username: String): AccountOwner?

    @Query("SELECT * FROM account_owners WHERE mobileNumber = :mobileNumber LIMIT 1")
    suspend fun getAccountByMobileNumber(mobileNumber: String): AccountOwner?

    @Query("SELECT * FROM account_owners")
    suspend fun getAllAccounts(): List<AccountOwner>

    @Query("DELETE FROM account_owners")
    suspend fun clearAllAccounts()

    @Query("DELETE FROM account_owners WHERE mobileNumber = :mobileNumber")
    suspend fun deleteAccountByMobile(mobileNumber: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(accountOwner: AccountOwner)
}
