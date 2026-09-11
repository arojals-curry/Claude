package com.arnau.usagestats.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val iconCachePath: String? = null,
    val isHidden: Boolean = false,
    val category: String? = null
)
