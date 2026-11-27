package com.example.feature.bookmarks.repository

import com.example.core.database.dao.BookmarkDao
import com.example.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(
    private val bookmarkDao: BookmarkDao
) {
    fun observeAll(): Flow<List<BookmarkEntity>> = bookmarkDao.observeAllBookmarks()

    fun observeIsBookmarked(url: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(url)

    suspend fun addOrUpdate(title: String, url: String, folder: String = "General") {
        val existing = bookmarkDao.getByUrl(url)
        bookmarkDao.upsertBookmark(
            BookmarkEntity(
                id = existing?.id ?: 0L,
                title = title.ifBlank { url },
                url = url,
                folder = folder.ifBlank { "General" },
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleBookmark(title: String, url: String): Boolean {
        val existing = bookmarkDao.getByUrl(url)
        return if (existing != null) {
            bookmarkDao.deleteById(existing.id)
            false
        } else {
            bookmarkDao.upsertBookmark(
                BookmarkEntity(
                    title = title.ifBlank { url },
                    url = url
                )
            )
            true
        }
    }

    suspend fun searchQuick(query: String): List<BookmarkEntity> =
        bookmarkDao.searchQuick(query)

    suspend fun deleteById(id: Long) {
        bookmarkDao.deleteById(id)
    }
}
