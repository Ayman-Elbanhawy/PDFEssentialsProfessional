package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SigningIdentityDao {
    @Query("SELECT * FROM signing_identities ORDER BY createdAtEpochMillis DESC")
    suspend fun all(): List<SigningIdentityEntity>

    @Query("SELECT * FROM signing_identities WHERE id = :id")
    suspend fun findById(id: String): SigningIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SigningIdentityEntity)
}
