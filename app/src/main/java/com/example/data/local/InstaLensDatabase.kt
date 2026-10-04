package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_profile_links")
data class SavedProfileLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val usernameOrQuery: String,
    val displayTitle: String,
    val instagramUrl: String,
    val publicWebSearchUrl: String,
    val clueSource: String,
    val notes: String,
    val photoSignatureHash: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val queryOrClue: String,
    val searchType: String,
    val primaryUrl: String,
    val candidateCount: Int,
    val summaryMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface InstaLensDao {
    @Query("SELECT * FROM saved_profile_links ORDER BY createdAt DESC")
    fun getAllSavedLinks(): Flow<List<SavedProfileLinkEntity>>

    @Query("SELECT * FROM saved_profile_links ORDER BY createdAt DESC")
    suspend fun getAllSavedLinksSnapshot(): List<SavedProfileLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedLink(link: SavedProfileLinkEntity): Long

    @Query("DELETE FROM saved_profile_links WHERE id = :id")
    suspend fun deleteSavedLinkById(id: Int)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    fun getAllSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(item: SearchHistoryEntity): Long

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchHistoryById(id: Int)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Database(
    entities = [
        SavedProfileLinkEntity::class,
        SearchHistoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class InstaLensDatabase : RoomDatabase() {
    abstract fun instaLensDao(): InstaLensDao

    companion object {
        @Volatile
        private var INSTANCE: InstaLensDatabase? = null

        fun getInstance(context: Context): InstaLensDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InstaLensDatabase::class.java,
                    "instalens_ml_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
