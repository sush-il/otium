package dev.sushil.otium.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import dev.sushil.otium.domain.model.RuleType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "site_limits", indices = [Index("host")])
data class SiteLimitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val type: RuleType,
    val dailyLimitMinutes: Int?,
    val hourlyLimitMinutes: Int?,
    val launchLimit: Int?,
    val blockUntil: Long?,
    val daysMask: Int?,
    val startMinute: Int?,
    val endMinute: Int?,
)

@Dao
interface SiteLimitDao {
    @Query("SELECT * FROM site_limits ORDER BY host, id")
    fun observeAll(): Flow<List<SiteLimitEntity>>

    @Insert
    suspend fun insert(entity: SiteLimitEntity): Long

    @Query("DELETE FROM site_limits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM site_limits WHERE host = :host")
    suspend fun deleteByHost(host: String)

    @Query("DELETE FROM site_limits WHERE host = :host AND type = :type")
    suspend fun deleteByHostAndType(host: String, type: RuleType)

    @Query("SELECT COUNT(*) FROM site_limits WHERE host = :host AND type = 'ALWAYS'")
    suspend fun alwaysCount(host: String): Int
}
