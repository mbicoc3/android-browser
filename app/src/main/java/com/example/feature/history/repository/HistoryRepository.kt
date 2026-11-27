package com.example.feature.history.repository

import com.example.core.database.dao.HistoryDao
import com.example.core.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(
    private val historyDao: HistoryDao
) {
    fun observeHistory(): Flow<List<HistoryEntity>> = historyDao.observeHistory()

    suspend fun recordVisit(title: String, url: String) {
        historyDao.insertVisit(
            HistoryEntity(
                title = title.ifBlank { url },
                url = url,
                visitedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun searchQuick(query: String): List<HistoryEntity> =
        historyDao.searchQuick(query)

    suspend fun deleteEntry(id: Long) {
        historyDao.deleteById(id)
    }

    suspend fun clearAll() {
        historyDao.clearAll()
    }
}
