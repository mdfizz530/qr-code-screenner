package com.example.data

import kotlinx.coroutines.flow.Flow

class QrRepository(private val qrDao: QrDao) {
    val allHistory: Flow<List<QrItem>> = qrDao.getAllHistory()

    suspend fun insert(item: QrItem): Long {
        return qrDao.insertItem(item)
    }

    suspend fun delete(id: Long) {
        qrDao.deleteItemById(id)
    }

    suspend fun clearAll() {
        qrDao.clearAllHistory()
    }
}
