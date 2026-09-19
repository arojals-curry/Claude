package com.arnau.usagestats.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {

    /** packageName es la clave primaria, así que REPLACE actúa como upsert. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: AppEntity)

    @Query("SELECT * FROM apps WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): AppEntity?
}
