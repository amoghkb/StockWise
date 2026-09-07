package com.example.stockwise.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.stockwise.data.dao.ItemDao
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.ItemWithCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepository @Inject constructor(
    private val itemDao: ItemDao
) {

    suspend fun insertItem(item: Item): String {
        itemDao.insertItem(item)
        return item.id
    }

    suspend fun insertItem(
        categoryId: String,
        name: String,
        originalPrice: Double,
        sellingPrice: Double,
        stock: Int,
        description: String? = null,
        imageUri: String? = null
    ): String {
        val item = Item(
            id = UUID.randomUUID().toString(),
            categoryId = categoryId,
            name = name,
            originalPrice = originalPrice,
            sellingPrice = sellingPrice,
            stock = stock,
            description = description,
            imageUri = imageUri,
            createdAt = Date(),
            updatedAt = Date()
        )
        itemDao.insertItem(item)
        return item.id
    }

    suspend fun updateItem(item: Item) {
        val updatedItem = item.copy(updatedAt = Date())
        itemDao.updateItem(updatedItem)
    }

    suspend fun updateItemImage(itemId: String, imageUri: String?) {
        val item = itemDao.getItemById(itemId)
        item?.let {
            val updatedItem = it.copy(
                imageUri = imageUri,
                updatedAt = Date()
            )
            itemDao.updateItem(updatedItem)
        }
    }

    suspend fun softDeleteItem(itemId: String) {
        itemDao.softDeleteItem(itemId, System.currentTimeMillis())
    }

    fun getAllActiveItemsWithCategory(): Flow<List<ItemWithCategory>> {
        return itemDao.getAllActiveItemsWithCategory()
    }

    fun getDeletedItemsWithCategory(): Flow<List<ItemWithCategory>> {
        return itemDao.getDeletedItemsWithCategory()
    }

    suspend fun getActiveItemWithCategoryById(itemId: String): ItemWithCategory? {
        return itemDao.getActiveItemWithCategoryById(itemId)
    }

    fun getActiveItemsByCategory(categoryId: String): Flow<List<ItemWithCategory>> {
        return itemDao.getActiveItemsByCategory(categoryId)
    }

    fun searchActiveItems(query: String): Flow<List<ItemWithCategory>> {
        return if (query.isBlank()) {
            itemDao.getAllActiveItemsWithCategory()
        } else {
            itemDao.searchActiveItems(query)
        }
    }

    suspend fun getActiveItemCount(): Int {
        return itemDao.getActiveItemCount()
    }

    suspend fun getActiveTotalStockByCategory(categoryId: String): Int {
        return itemDao.getActiveTotalStockByCategory(categoryId)
    }

    suspend fun getTotalActiveStock(): Int {
        return itemDao.getTotalActiveStock()
    }

    suspend fun getLowStockCount(threshold: Int = 5): Int {
        return itemDao.getLowStockCount(threshold)
    }

    fun getLowStockItems(threshold: Int = 5): Flow<List<ItemWithCategory>> {
        return itemDao.getLowStockItems(threshold)
    }

    suspend fun getItemById(itemId: String): Item? {
        return itemDao.getItemById(itemId)
    }

    // ==============================
    // OPTIMIZED IMAGE HELPER METHODS
    // ==============================

    /**
     * Save image to internal storage with optimization
     * This compresses the image to reduce file size and improve loading speed
     */
    suspend fun saveOptimizedImage(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    return@withContext null
                }

                // Decode bitmap with options for optimization
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                // Calculate sample size for downsampling
                val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)

                // Decode with sample size
                val newInputStream = context.contentResolver.openInputStream(uri)
                val bitmapOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565 // Reduces memory usage
                }
                val bitmap = BitmapFactory.decodeStream(newInputStream, null, bitmapOptions)
                newInputStream?.close()

                if (bitmap == null) {
                    return@withContext null
                }

                // Compress and save
                val fileName = "item_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, "images")
                if (!file.exists()) {
                    file.mkdirs()
                }
                val outputFile = File(file, fileName)
                val outputStream = FileOutputStream(outputFile)

                // Compress to JPEG with 80% quality
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.close()
                bitmap.recycle()

                return@withContext outputFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    /**
     * Calculate sample size for downsampling images
     * Max dimensions are 300x300 for thumbnails
     */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        val maxSize = 300
        var sampleSize = 1
        while (width / sampleSize > maxSize || height / sampleSize > maxSize) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Check if image file exists and is valid
     */
    suspend fun isImageValid(imagePath: String?): Boolean {
        if (imagePath.isNullOrEmpty()) return false
        return withContext(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                file.exists() && file.length() > 0
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Get image file size in KB
     */
    suspend fun getImageSize(imagePath: String?): Long {
        if (imagePath.isNullOrEmpty()) return 0
        return withContext(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                if (file.exists()) file.length() / 1024 else 0
            } catch (e: Exception) {
                0
            }
        }
    }

    /**
     * Delete unused images to free up space
     */
    suspend fun cleanupUnusedImages(context: Context, activeItemIds: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                val imagesDir = File(context.filesDir, "images")
                if (!imagesDir.exists()) return@withContext

                val imageFiles = imagesDir.listFiles() ?: return@withContext

                // Get all active image URIs from items
                val activeImagePaths = activeItemIds.mapNotNull { itemId ->
                    getItemById(itemId)?.imageUri
                }.toSet()

                // Delete images not associated with any active item
                imageFiles.forEach { file ->
                    val filePath = file.absolutePath
                    if (!activeImagePaths.contains(filePath) && file.name.startsWith("item_")) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}