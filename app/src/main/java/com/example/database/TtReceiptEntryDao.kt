package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TtReceiptEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: TtReceiptEntry)

    @Query("SELECT * FROM tt_receipt_entries ORDER BY timestamp DESC")
    suspend fun getAllEntries(): List<TtReceiptEntry>

    @Query("SELECT * FROM tt_receipt_entries WHERE date = :date ORDER BY timestamp DESC")
    suspend fun getEntriesByDate(date: String): List<TtReceiptEntry>

    @Delete
    suspend fun deleteEntry(entry: TtReceiptEntry)

    @Query("DELETE FROM tt_receipt_entries")
    suspend fun clearAllEntries()
}
