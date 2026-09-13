package com.example.stockwise.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.stockwise.data.entities.ProcurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcurementDao {

    // ---------- Insert / Update / Delete ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ProcurementEntity): Long

    @Update
    suspend fun updateItem(item: ProcurementEntity)

    @Delete
    suspend fun deleteItem(item: ProcurementEntity)

    @Query("DELETE FROM procurement_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM procurement_items WHERE dateKey = :dateKey")
    suspend fun deleteForDate(dateKey: String)

    // ---------- Query by date ----------

    @Query("SELECT * FROM procurement_items WHERE dateKey = :dateKey ORDER BY createdAt ASC")
    fun getItemsForDate(dateKey: String): Flow<List<ProcurementEntity>>

    @Query("SELECT * FROM procurement_items WHERE dateKey = :dateKey ORDER BY createdAt ASC")
    suspend fun getItemsForDateOnce(dateKey: String): List<ProcurementEntity>

    @Query("SELECT * FROM procurement_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProcurementEntity?

    // ---------- Match helpers ----------

    @Query("""
        SELECT * FROM procurement_items 
        WHERE dateKey = :dateKey 
          AND isCustom = 0 
          AND itemId = :itemId 
        LIMIT 1
    """)
    suspend fun findCatalogueItem(dateKey: String, itemId: String): ProcurementEntity?

    @Query("""
        SELECT * FROM procurement_items 
        WHERE dateKey = :dateKey 
          AND isCustom = 1 
          AND LOWER(customName) = LOWER(:name) 
        LIMIT 1
    """)
    suspend fun findCustomItem(dateKey: String, name: String): ProcurementEntity?

    // ---------- Counts / stats ----------

    @Query("SELECT COUNT(*) FROM procurement_items WHERE dateKey = :dateKey")
    suspend fun countForDate(dateKey: String): Int

    @Query("SELECT COUNT(*) FROM procurement_items WHERE dateKey = :dateKey AND isPurchased = 0")
    suspend fun countPendingForDate(dateKey: String): Int

    @Query("SELECT COUNT(*) FROM procurement_items WHERE dateKey = :dateKey AND isPurchased = 1")
    suspend fun countPurchasedForDate(dateKey: String): Int

    @Query("SELECT SUM(quantity) FROM procurement_items WHERE dateKey = :dateKey")
    suspend fun totalQuantityForDate(dateKey: String): Int?

    // ---------- Dates that have items (for calendar dot) ----------

    @Query("SELECT DISTINCT dateKey FROM procurement_items")
    fun getAllActiveDateKeys(): Flow<List<String>>

    // ---------- Transactional addOrUpdate ----------

    @Transaction
    suspend fun addOrUpdate(entity: ProcurementEntity): Long {
        val existing = if (entity.isCustom) {
            entity.customName?.let { findCustomItem(entity.dateKey, it) }
        } else {
            entity.itemId?.let { findCatalogueItem(entity.dateKey, it) }
        }

        return if (existing != null) {
            updateItem(
                existing.copy(
                    quantity = existing.quantity + entity.quantity,
                    notes = entity.notes.ifBlank { existing.notes },
                    isPurchased = entity.isPurchased,
                    updatedAt = entity.updatedAt
                )
            )
            existing.id
        } else {
            insertItem(entity)
        }
    }
    @Query("""
    SELECT DISTINCT dateKey
    FROM procurement_items
    WHERE dateKey LIKE :monthPrefix || '%'
    ORDER BY dateKey ASC
""")
    fun getActiveDateKeysForMonth(monthPrefix: String): Flow<List<String>>
}