package com.example.stockwise.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.stockwise.data.entities.Vehicle
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.ItemWithCategory
import com.example.stockwise.data.entities.ItemVehicleRelation
import com.example.stockwise.data.entities.ItemWithCategoryAndVehicles
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item): Long

    @Update
    suspend fun updateItem(item: Item)

    @Query("UPDATE items SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :itemId")
    suspend fun softDeleteItem(itemId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 0 
        ORDER BY items.name ASC
    """)
    fun getAllActiveItemsWithCategory(): Flow<List<ItemWithCategory>>

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 1 
        ORDER BY items.deletedAt DESC
    """)
    fun getDeletedItemsWithCategory(): Flow<List<ItemWithCategory>>

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.id = :itemId AND items.isDeleted = 0
    """)
    suspend fun getActiveItemWithCategoryById(itemId: String): ItemWithCategory?

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.categoryId = :categoryId AND items.isDeleted = 0
    """)
    fun getActiveItemsByCategory(categoryId: String): Flow<List<ItemWithCategory>>

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 0 
        AND (items.name LIKE '%' || :query || '%' 
        OR items.description LIKE '%' || :query || '%'
        OR categories.name LIKE '%' || :query || '%')
        ORDER BY items.name ASC
    """)
    fun searchActiveItems(query: String): Flow<List<ItemWithCategory>>

    @Query("SELECT COUNT(*) FROM items WHERE isDeleted = 0")
    suspend fun getActiveItemCount(): Int

    @Query("SELECT SUM(stock) FROM items WHERE categoryId = :categoryId AND isDeleted = 0")
    suspend fun getActiveTotalStockByCategory(categoryId: String): Int

    @Query("SELECT SUM(stock) FROM items WHERE isDeleted = 0")
    suspend fun getTotalActiveStock(): Int

    @Query("SELECT COUNT(*) FROM items WHERE stock <= :threshold AND isDeleted = 0")
    suspend fun getLowStockCount(threshold: Int = 5): Int

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 0 AND items.stock <= :threshold
        ORDER BY items.stock ASC
    """)
    fun getLowStockItems(threshold: Int = 5): Flow<List<ItemWithCategory>>

    @Query("SELECT * FROM items WHERE id = :itemId AND isDeleted = 0")
    suspend fun getItemById(itemId: String): Item?

    // ==================== VEHICLE RELATIONSHIP METHODS ====================

    @Transaction
    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 0 
        ORDER BY items.name ASC
    """)
    fun getAllActiveItemsWithVehicles(): Flow<List<ItemWithCategoryAndVehicles>>

    @Transaction
    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.id = :itemId AND items.isDeleted = 0
    """)
    suspend fun getActiveItemWithVehiclesById(itemId: String): ItemWithCategoryAndVehicles?

    @Transaction
    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.categoryId = :categoryId AND items.isDeleted = 0
    """)
    fun getActiveItemsByCategoryWithVehicles(categoryId: String): Flow<List<ItemWithCategoryAndVehicles>>

    @Transaction
    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 0 
        AND (items.name LIKE '%' || :query || '%' 
        OR items.description LIKE '%' || :query || '%'
        OR categories.name LIKE '%' || :query || '%')
        ORDER BY items.name ASC
    """)
    fun searchActiveItemsWithVehicles(query: String): Flow<List<ItemWithCategoryAndVehicles>>

    @Transaction
    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 0 AND items.stock <= :threshold
        ORDER BY items.stock ASC
    """)
    fun getLowStockItemsWithVehicles(threshold: Int = 5): Flow<List<ItemWithCategoryAndVehicles>>

    @Transaction
    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 1 
        ORDER BY items.deletedAt DESC
    """)
    fun getDeletedItemsWithVehicles(): Flow<List<ItemWithCategoryAndVehicles>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemVehicleRelation(relation: ItemVehicleRelation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemVehicleRelations(relations: List<ItemVehicleRelation>)

    @Query("DELETE FROM item_vehicle_relations WHERE itemId = :itemId")
    suspend fun deleteAllVehicleRelationsForItem(itemId: String)

    @Query("DELETE FROM item_vehicle_relations WHERE itemId = :itemId AND vehicleId = :vehicleId")
    suspend fun deleteVehicleRelation(itemId: String, vehicleId: String)

    @Query("SELECT * FROM item_vehicle_relations WHERE itemId = :itemId")
    suspend fun getVehicleRelationsForItem(itemId: String): List<ItemVehicleRelation>

    @Query("""
        SELECT v.* FROM item_vehicle_relations ivr
        INNER JOIN vehicles v ON ivr.vehicleId = v.id
        WHERE ivr.itemId = :itemId AND v.isDeleted = 0
    """)
    suspend fun getActiveVehiclesForItem(itemId: String): List<Vehicle>

    @Query("""
        SELECT COUNT(*) FROM item_vehicle_relations ivr
        INNER JOIN vehicles v ON ivr.vehicleId = v.id
        WHERE ivr.itemId = :itemId AND v.isDeleted = 0
    """)
    suspend fun getVehicleCountForItem(itemId: String): Int

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM item_vehicle_relations 
            WHERE itemId = :itemId AND vehicleId = :vehicleId
        )
    """)
    suspend fun isVehicleAssignedToItem(itemId: String, vehicleId: String): Boolean

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        INNER JOIN item_vehicle_relations ivr ON items.id = ivr.itemId
        WHERE ivr.vehicleId = :vehicleId AND items.isDeleted = 0
    """)
    fun getItemsByVehicleWithCategory(vehicleId: String): Flow<List<ItemWithCategory>>

    @Transaction
    suspend fun updateItemVehicles(itemId: String, vehicleIds: List<String>) {
        deleteAllVehicleRelationsForItem(itemId)
        if (vehicleIds.isNotEmpty()) {
            val relations = vehicleIds.map { vehicleId ->
                ItemVehicleRelation(itemId = itemId, vehicleId = vehicleId)
            }
            insertItemVehicleRelations(relations)
        }
    }
}