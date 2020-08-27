package com.example.android.codelabs.paging.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteKeysDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(remoteKeys: List<RemoteKeys>)

    @Query("SELECT * from remote_keys where repoId = :repoId")
    suspend fun remoteKeysRepoId(repoId: String): RemoteKeys?

    @Query("DELETE from remote_keys")
    suspend fun clearRemoteKeys()

}