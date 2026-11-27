package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filter_lists")
data class FilterListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceUrl: String,
    val category: String,
    val enabled: Boolean,
    val ruleCount: Int,
    val cosmeticRuleCount: Int,
    val lastUpdatedAt: Long,
    val isBuiltIn: Boolean
)
