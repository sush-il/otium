package com.focusblock.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.focusblock.domain.model.RuleType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_limits", indices = [Index("packageName")])
data class AppLimitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val type: RuleType,
    val dailyLimitMinutes: Int?,
    val hourlyLimitMinutes: Int?,
    val launchLimit: Int?,
    val blockUntil: Long?, // epoch ms
    val daysMask: Int?,
    val startMinute: Int?,
    val endMinute: Int?,
)

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits ORDER BY packageName, id")
    fun observeAll(): Flow<List<AppLimitEntity>>

    @Insert
    suspend fun insert(entity: AppLimitEntity): Long

    @Query("DELETE FROM app_limits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM app_limits WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)

    @Query("SELECT COUNT(*) FROM app_limits WHERE packageName = :pkg AND type = 'ALWAYS'")
    suspend fun alwaysCount(pkg: String): Int
}
