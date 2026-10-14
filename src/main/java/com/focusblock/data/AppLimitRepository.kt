package com.focusblock.data

import com.focusblock.data.db.AppLimitDao
import com.focusblock.data.db.AppLimitEntity
import com.focusblock.domain.model.AppLimit
import com.focusblock.domain.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Stackable per-app block rules. The rest of the app uses [AppLimit]. */
class AppLimitRepository(private val dao: AppLimitDao) {

    val limits: Flow<List<AppLimit>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun add(limit: AppLimit): Long = dao.insert(limit.toEntity())

    suspend fun removeRule(id: Long) = dao.delete(id)

    suspend fun removeApp(pkg: String) = dao.deleteByPackage(pkg)

    /** Used when migrating old always-blocked apps: add an ALWAYS rule if the app has none. */
    suspend fun addAlwaysIfAbsent(pkg: String) {
        if (dao.alwaysCount(pkg) == 0) dao.insert(AppLimit(packageName = pkg, type = com.focusblock.domain.model.RuleType.ALWAYS).toEntity())
    }

    private fun AppLimitEntity.toDomain() = AppLimit(
        id = id,
        packageName = packageName,
        type = type,
        dailyLimitMinutes = dailyLimitMinutes,
        hourlyLimitMinutes = hourlyLimitMinutes,
        launchLimit = launchLimit,
        blockUntil = blockUntil?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
        schedule = daysMask?.let { Schedule(it, startMinute ?: 0, endMinute ?: 0) },
    )

    private fun AppLimit.toEntity() = AppLimitEntity(
        id = id,
        packageName = packageName,
        type = type,
        dailyLimitMinutes = dailyLimitMinutes,
        hourlyLimitMinutes = hourlyLimitMinutes,
        launchLimit = launchLimit,
        blockUntil = blockUntil?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        daysMask = schedule?.daysMask,
        startMinute = schedule?.startMinute,
        endMinute = schedule?.endMinute,
    )
}
