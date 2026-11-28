package com.example.feature.collections.repository

import android.net.Uri
import com.example.core.common.UrlUtils
import com.example.core.database.dao.ImageCollectionDao
import com.example.core.database.entity.ImageCollectionEntity
import kotlinx.coroutines.flow.Flow

class ImageCollectionRepository(
    private val dao: ImageCollectionDao
) {
    fun observeAll(): Flow<List<ImageCollectionEntity>> = dao.observeAll()

    fun observeIsCollected(imageUrl: String): Flow<Boolean> = dao.observeIsCollected(imageUrl)

    suspend fun collectImage(
        title: String,
        imageUrl: String,
        sourcePageUrl: String = "",
        collectionName: String = DEFAULT_COLLECTION
    ) {
        val cleanUrl = imageUrl.trim()
        if (cleanUrl.isBlank()) return
        val existing = dao.getByImageUrl(cleanUrl)
        val resolvedTitle = title.trim().ifBlank { deriveTitleFromUrl(cleanUrl) }
        val resolvedFolder = collectionName.trim().ifBlank { DEFAULT_COLLECTION }
        dao.upsert(
            ImageCollectionEntity(
                id = existing?.id ?: 0L,
                title = resolvedTitle,
                imageUrl = cleanUrl,
                sourcePageUrl = sourcePageUrl.trim().ifBlank { existing?.sourcePageUrl.orEmpty() },
                collectionName = resolvedFolder,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun moveToCollection(id: Long, collectionName: String) {
        val resolved = collectionName.trim().ifBlank { DEFAULT_COLLECTION }
        dao.updateCollectionName(id = id, collectionName = resolved)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun deriveTitleFromUrl(url: String): String {
        val lastSegment = runCatching { Uri.parse(url).lastPathSegment }
            .getOrNull()
            ?.substringBefore('?')
            ?.trim()
            .orEmpty()
        return lastSegment.ifBlank { UrlUtils.extractHost(url).ifBlank { "Image" } }
    }

    companion object {
        const val DEFAULT_COLLECTION = "General"
    }
}
