package dev.sushil.otium.data

import dev.sushil.otium.data.db.AppLimitDao
import dev.sushil.otium.data.db.AppLimitEntity
import dev.sushil.otium.domain.model.AppLimit
import dev.sushil.otium.domain.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Stackable per-app block rules. The rest of the app uses [AppLimit]. */
class AppLimitRepository(private val dao: AppLimitDao) {

    val limits: Flow<List<AppLimit>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Each rule type is unique per app, so adding one replaces an existing rule of that type. */
    suspend fun add(limit: AppLimit): Long {
        dao.deleteByPackageAndType(limit.packageName, limit.type)
        return dao.insert(limit.toEntity())
    }

    suspend fun removeRule(id: Long) = dao.delete(id)

    suspend fun removeApp(pkg: String) = dao.deleteByPackage(pkg)

    /** Used when migrating old always-blocked apps: add an ALWAYS rule if the app has none. */
    suspend fun addAlwaysIfAbsent(pkg: String) {
        if (dao.alwaysCount(pkg) == 0) dao.insert(AppLimit(packageName = pkg, type = dev.sushil.otium.domain.model.RuleType.ALWAYS).toEntity())
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
