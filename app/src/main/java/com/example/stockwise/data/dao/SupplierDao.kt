package com.example.stockwise.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.stockwise.data.entities.Supplier
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier): Long

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Query("UPDATE suppliers SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :supplierId")
    suspend fun softDeleteSupplier(supplierId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM suppliers WHERE isDeleted = 0 ORDER BY companyName ASC")
    fun getAllActiveSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE id = :supplierId AND isDeleted = 0")
    suspend fun getSupplierById(supplierId: String): Supplier?

    @Query("""
        SELECT * FROM suppliers 
        WHERE isDeleted = 0 
        AND (companyName LIKE '%' || :query || '%' 
             OR contactPerson LIKE '%' || :query || '%' 
             OR category LIKE '%' || :query || '%'
             OR phoneNumber LIKE '%' || :query || '%')
        ORDER BY companyName ASC
    """)
    fun searchActiveSuppliers(query: String): Flow<List<Supplier>>

    @Query("SELECT COUNT(*) FROM suppliers WHERE isDeleted = 0")
    suspend fun getActiveSupplierCount(): Int
}