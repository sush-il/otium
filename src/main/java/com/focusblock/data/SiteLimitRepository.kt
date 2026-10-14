package com.focusblock.data

import com.focusblock.data.db.SiteLimitDao
import com.focusblock.data.db.SiteLimitEntity
import com.focusblock.domain.model.RuleType
import com.focusblock.domain.model.Schedule
import com.focusblock.domain.model.SiteLimit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Stackable per-site block rules. The rest of the app uses [SiteLimit]. */
class SiteLimitRepository(private val dao: SiteLimitDao) {

    val limits: Flow<List<SiteLimit>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Each rule type is unique per host, so adding one replaces an existing rule of that type. */
    suspend fun add(limit: SiteLimit): Long {
        dao.deleteByHostAndType(limit.host, limit.type)
        return dao.insert(limit.toEntity())
    }

    suspend fun removeRule(id: Long) = dao.delete(id)

    suspend fun removeHost(host: String) = dao.deleteByHost(host)

    suspend fun addAlwaysIfAbsent(host: String) {
        if (dao.alwaysCount(host) == 0) dao.insert(SiteLimit(host = host, type = RuleType.ALWAYS).toEntity())
    }

    private fun SiteLimitEntity.toDomain() = SiteLimit(
        id = id, host = host, type = type,
        dailyLimitMinutes = dailyLimitMinutes, hourlyLimitMinutes = hourlyLimitMinutes, launchLimit = launchLimit,
        blockUntil = blockUntil?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
        schedule = daysMask?.let { Schedule(it, startMinute ?: 0, endMinute ?: 0) },
    )

    private fun SiteLimit.toEntity() = SiteLimitEntity(
        id = id, host = host, type = type,
        dailyLimitMinutes = dailyLimitMinutes, hourlyLimitMinutes = hourlyLimitMinutes, launchLimit = launchLimit,
        blockUntil = blockUntil?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        daysMask = schedule?.daysMask, startMinute = schedule?.startMinute, endMinute = schedule?.endMinute,
    )
}
