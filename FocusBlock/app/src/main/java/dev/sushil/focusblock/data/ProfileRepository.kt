package dev.sushil.focusblock.data

import dev.sushil.focusblock.data.db.ProfileDao
import dev.sushil.focusblock.data.db.ProfileEntity
import dev.sushil.focusblock.data.db.ProfileWithDetails
import dev.sushil.focusblock.data.db.ScheduleEntity
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.domain.model.Schedule
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The only place that knows about Room types; the rest of the app uses [Profile]. */
class ProfileRepository(private val dao: ProfileDao) {

    val profiles: Flow<List<Profile>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): Profile? = dao.get(id)?.toDomain()

    suspend fun save(profile: Profile): Long = dao.save(
        profile = ProfileEntity(
            id = profile.id,
            name = profile.name,
            enabled = profile.enabled,
            type = profile.type,
            dailyLimitMinutes = profile.dailyLimitMinutes,
            createdAt = System.currentTimeMillis(),
            hourlyLimitMinutes = profile.hourlyLimitMinutes,
            launchLimit = profile.launchLimit,
            blockUntil = profile.blockUntil?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        ),
        packages = profile.packages,
        schedules = profile.schedules.map {
            ScheduleEntity(profileId = profile.id, daysMask = it.daysMask, startMinute = it.startMinute, endMinute = it.endMinute)
        },
    )

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(id: Long) = dao.delete(id)

    private fun ProfileWithDetails.toDomain() = Profile(
        id = profile.id,
        name = profile.name,
        enabled = profile.enabled,
        type = profile.type,
        packages = apps.mapTo(mutableSetOf()) { it.packageName },
        dailyLimitMinutes = profile.dailyLimitMinutes,
        schedules = schedules.map { Schedule(it.daysMask, it.startMinute, it.endMinute) },
        hourlyLimitMinutes = profile.hourlyLimitMinutes,
        launchLimit = profile.launchLimit,
        blockUntil = profile.blockUntil?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
        },
    )
}
