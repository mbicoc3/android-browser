package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "site_records",
    indices = [
        Index(value = ["host"]),
        Index(value = ["lastVisitedAt"])
    ]
)
data class SiteRecordEntity(
    @PrimaryKey val origin: String,
    val host: String,
    val title: String = "",
    val lastVisitedAt: Long = System.currentTimeMillis(),
    val visitCount: Int = 1,
    val cookieCount: Int = 0,
    val localStorageCount: Int = 0,
    val localStorageBytes: Long = 0L,
    val sessionStorageCount: Int = 0,
    val sessionStorageBytes: Long = 0L,
    val indexedDbCount: Int = 0,
    val cacheStorageCount: Int = 0,
    val serviceWorkerCount: Int = 0,
    val webStorageUsageBytes: Long = 0L,
    val webStorageQuotaBytes: Long = 0L,
    val lastInspectedAt: Long = 0L
)
