package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "filter_rules",
    indices = [
        Index(value = ["enabled"]),
        Index(value = ["ruleType"])
    ]
)
data class FilterRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rawRule: String,
    val ruleType: String,
    val domainFilter: String,
    val enabled: Boolean,
    val createdAt: Long
)
