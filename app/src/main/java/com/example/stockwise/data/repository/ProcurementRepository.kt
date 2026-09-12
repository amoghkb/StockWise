package com.example.stockwise.data.repository

import com.example.stockwise.data.dao.ProcurementDao
import com.example.stockwise.data.entities.ProcurementEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcurementRepository @Inject constructor(
    private val procurementDao: ProcurementDao
) {

    // ---------- Insert / Update / Delete ----------

    /** Insert as a new row (no quantity summing). */
    suspend fun insert(entity: ProcurementEntity): Long =
        procurementDao.insertItem(entity)

    /** Add or bump quantity if a matching row already exists for the date. */
    suspend fun addOrUpdate(entity: ProcurementEntity): Long =
        procurementDao.addOrUpdate(entity)

    suspend fun updateItem(item: ProcurementEntity) =
        procurementDao.updateItem(item.copy(updatedAt = Date()))

    suspend fun deleteItem(item: ProcurementEntity) =
        procurementDao.deleteItem(item)

    suspend fun deleteById(id: Long) =
        procurementDao.deleteById(id)

    suspend fun deleteForDate(dateKey: String) =
        procurementDao.deleteForDate(dateKey)

    // ---------- Query by date ----------

    fun getItemsForDate(dateKey: String): Flow<List<ProcurementEntity>> =
        procurementDao.getItemsForDate(dateKey)

    suspend fun getItemsForDateOnce(dateKey: String): List<ProcurementEntity> =
        procurementDao.getItemsForDateOnce(dateKey)

    suspend fun getById(id: Long): ProcurementEntity? =
        procurementDao.getById(id)

    // ---------- Counts / stats ----------

    suspend fun countForDate(dateKey: String): Int =
        procurementDao.countForDate(dateKey)

    suspend fun countPendingForDate(dateKey: String): Int =
        procurementDao.countPendingForDate(dateKey)

    suspend fun countPurchasedForDate(dateKey: String): Int =
        procurementDao.countPurchasedForDate(dateKey)

    suspend fun totalQuantityForDate(dateKey: String): Int =
        procurementDao.totalQuantityForDate(dateKey) ?: 0

    // ---------- Calendar dots ----------

    fun getAllActiveDateKeys(): Flow<List<String>> =
        procurementDao.getAllActiveDateKeys()

    fun getActiveDateKeysForMonth(monthPrefix: String): Flow<List<String>> =
        procurementDao.getActiveDateKeysForMonth(monthPrefix)
}