package dev.sushil.focusblock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.sushil.focusblock.domain.model.RuleType
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProfileDao {

    @Transaction
    @Query("SELECT * FROM profiles ORDER BY createdAt")
    abstract fun observeAll(): Flow<List<ProfileWithDetails>>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :id")
    abstract suspend fun get(id: Long): ProfileWithDetails?

    @Query("UPDATE profiles SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE profiles SET pausedUntil = :until WHERE id = :id")
    abstract suspend fun setPausedUntil(id: Long, until: Long?)

    @Query("DELETE FROM profiles WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Insert or update a profile and replace its apps and schedules, all-or-nothing. */
    @Transaction
    open suspend fun save(
        profile: ProfileEntity,
        packages: Collection<String>,
        schedules: List<ScheduleEntity>,
    ): Long {
        val id = if (profile.id == 0L) {
            insertProfile(profile)
        } else {
            updateProfile(profile.id, profile.name, profile.enabled, profile.type, profile.dailyLimitMinutes, profile.hourlyLimitMinutes, profile.launchLimit, profile.blockUntil, profile.wholePhone)
            profile.id
        }
        deleteApps(id)
        insertApps(packages.map { ProfileAppEntity(id, it) })
        deleteSchedules(id)
        insertSchedules(schedules.map { it.copy(id = 0, profileId = id) })
        return id
    }

    @Insert protected abstract suspend fun insertProfile(profile: ProfileEntity): Long
    @Insert protected abstract suspend fun insertApps(apps: List<ProfileAppEntity>)
    @Insert protected abstract suspend fun insertSchedules(schedules: List<ScheduleEntity>)

    // Leaves createdAt alone so edited profiles keep their place in the list
    @Query(
        "UPDATE profiles SET name = :name, enabled = :enabled, type = :type, dailyLimitMinutes = :dailyLimitMinutes, " +
            "hourlyLimitMinutes = :hourlyLimitMinutes, launchLimit = :launchLimit, blockUntil = :blockUntil, " +
            "wholePhone = :wholePhone WHERE id = :id",
    )
    protected abstract suspend fun updateProfile(
        id: Long,
        name: String,
        enabled: Boolean,
        type: RuleType,
        dailyLimitMinutes: Int?,
        hourlyLimitMinutes: Int?,
        launchLimit: Int?,
        blockUntil: Long?,
        wholePhone: Boolean,
    )

    @Query("DELETE FROM profile_apps WHERE profileId = :profileId")
    protected abstract suspend fun deleteApps(profileId: Long)

    @Query("DELETE FROM schedules WHERE profileId = :profileId")
    protected abstract suspend fun deleteSchedules(profileId: Long)
}
