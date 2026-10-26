package dev.sushil.otium.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import dev.sushil.otium.domain.model.AppLimit
import dev.sushil.otium.domain.model.Profile
import dev.sushil.otium.domain.model.RuleType
import dev.sushil.otium.domain.model.Schedule
import dev.sushil.otium.domain.model.SiteLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Saves and restores everything the user set up, as a plain JSON file they choose.
 * Deliberately excludes the password, mode and emergency state: a backup should never
 * carry something that could lock you out or that someone could swap in to weaken Strict.
 */
class BackupManager(
    private val context: Context,
    private val profiles: ProfileRepository,
    private val settings: SettingsStore,
    private val appLimits: AppLimitRepository,
    private val siteLimits: SiteLimitRepository,
) {
    /** What a restore did, for the confirmation message. */
    data class RestoreResult(val appsRestored: Int, val appsSkipped: Int, val sitesRestored: Int)

    suspend fun exportTo(uri: Uri): Unit = withContext(Dispatchers.IO) {
        val json = buildJson()
        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            ?: error("Could not open the file for writing")
    }

    suspend fun importFrom(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not open the file for reading")
        applyJson(JSONObject(text))
    }

    private suspend fun buildJson(): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("profiles", JSONArray().apply { profiles.all().forEach { put(it.toJson()) } })
        root.put("appLimits", JSONArray().apply { appLimits.limits.first().forEach { put(it.toJson()) } })
        root.put("siteLimits", JSONArray().apply { siteLimits.limits.first().forEach { put(it.toJson()) } })
        root.put("excludedApps", JSONArray(settings.excludedApps.first().toList()))
        root.put("blockedSites", JSONArray(settings.blockedSites.first().toList()))
        root.put("blockedKeywords", JSONArray(settings.blockedKeywords.first().toList()))
        root.put("timerDisplay", settings.timerDisplay.first().name)
        root.put("lowTimeAlerts", settings.lowTimeAlerts.first())
        root.put(
            "feeds",
            JSONObject()
                .put("youtubeShorts", settings.blockYoutubeShorts.first())
                .put("instagramReels", settings.blockInstagramReels.first())
                .put("facebookReels", settings.blockFacebookReels.first())
                .put("adult", settings.blockAdult.first()),
        )
        return root.toString(2)
    }

    private suspend fun applyJson(root: JSONObject): RestoreResult {
        val restored = buildList {
            val arr = root.optJSONArray("profiles") ?: JSONArray()
            for (i in 0 until arr.length()) add(arr.getJSONObject(i).toProfile())
        }
        profiles.replaceAll(restored)
        val result = restoreLimits(root)
        settings.setExcludedApps(root.stringSet("excludedApps"))
        settings.setBlockedSites(root.stringSet("blockedSites"))
        settings.setBlockedKeywords(root.stringSet("blockedKeywords"))
        root.optString("timerDisplay").takeIf { it.isNotEmpty() }?.let { name ->
            runCatching { TimerDisplay.valueOf(name) }.getOrNull()?.let { settings.setTimerDisplay(it) }
        }
        if (root.has("lowTimeAlerts")) settings.setLowTimeAlerts(root.getBoolean("lowTimeAlerts"))
        root.optJSONObject("feeds")?.let { f ->
            settings.setBlockYoutubeShorts(f.optBoolean("youtubeShorts"))
            settings.setBlockInstagramReels(f.optBoolean("instagramReels"))
            settings.setBlockFacebookReels(f.optBoolean("facebookReels"))
            settings.setBlockAdult(f.optBoolean("adult"))
        }
        return result
    }

    /**
     * Per-app and per-site rules. An app or site in the backup gets exactly the backed-up
     * rules (its current ones are replaced); anything not in the backup is left alone.
     * Apps that aren't installed on this phone are skipped. Old (v1) backups have none.
     */
    private suspend fun restoreLimits(root: JSONObject): RestoreResult {
        val apps = root.objects("appLimits").map { it.toAppLimit() }.groupBy { it.packageName }
        val (installed, missing) = apps.keys.partition { isInstalled(it) }
        installed.forEach { pkg ->
            appLimits.removeApp(pkg)
            apps.getValue(pkg).forEach { appLimits.add(it) }
        }
        val sites = root.objects("siteLimits").map { it.toSiteLimit() }.groupBy { it.host }
        sites.forEach { (host, rules) ->
            siteLimits.removeHost(host)
            rules.forEach { siteLimits.add(it) }
        }
        return RestoreResult(installed.size, missing.size, sites.size)
    }

    private fun isInstalled(pkg: String) = try {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun AppLimit.toJson() = JSONObject().put("packageName", packageName).putRule(type, dailyLimitMinutes, hourlyLimitMinutes, launchLimit, blockUntil, schedule)

    private fun SiteLimit.toJson() = JSONObject().put("host", host).putRule(type, dailyLimitMinutes, hourlyLimitMinutes, launchLimit, blockUntil, schedule)

    private fun JSONObject.putRule(
        type: RuleType, daily: Int?, hourly: Int?, launches: Int?, blockUntil: LocalDateTime?, schedule: Schedule?,
    ) = apply {
        put("type", type.name)
        daily?.let { put("dailyLimitMinutes", it) }
        hourly?.let { put("hourlyLimitMinutes", it) }
        launches?.let { put("launchLimit", it) }
        blockUntil?.let { put("blockUntil", it.toEpoch()) }
        schedule?.let { put("schedule", JSONObject().put("daysMask", it.daysMask).put("startMinute", it.startMinute).put("endMinute", it.endMinute)) }
    }

    private fun JSONObject.toAppLimit() = AppLimit(
        packageName = getString("packageName"),
        type = ruleType(),
        dailyLimitMinutes = optIntOrNull("dailyLimitMinutes"),
        hourlyLimitMinutes = optIntOrNull("hourlyLimitMinutes"),
        launchLimit = optIntOrNull("launchLimit"),
        blockUntil = if (has("blockUntil")) getLong("blockUntil").toLocalDateTime() else null,
        schedule = optJSONObject("schedule")?.let { Schedule(it.getInt("daysMask"), it.getInt("startMinute"), it.getInt("endMinute")) },
    )

    private fun JSONObject.toSiteLimit() = SiteLimit(
        host = getString("host"),
        type = ruleType(),
        dailyLimitMinutes = optIntOrNull("dailyLimitMinutes"),
        hourlyLimitMinutes = optIntOrNull("hourlyLimitMinutes"),
        launchLimit = optIntOrNull("launchLimit"),
        blockUntil = if (has("blockUntil")) getLong("blockUntil").toLocalDateTime() else null,
        schedule = optJSONObject("schedule")?.let { Schedule(it.getInt("daysMask"), it.getInt("startMinute"), it.getInt("endMinute")) },
    )

    private fun JSONObject.ruleType() = runCatching { RuleType.valueOf(getString("type")) }.getOrDefault(RuleType.ALWAYS)
    private fun JSONObject.optIntOrNull(key: String) = if (has(key)) getInt(key) else null
    private fun JSONObject.objects(key: String) = (optJSONArray(key) ?: JSONArray()).let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }

    private fun Profile.toJson() = JSONObject().apply {
        put("name", name)
        put("enabled", enabled)
        put("type", type.name)
        put("packages", JSONArray(packages.toList()))
        put("wholePhone", wholePhone)
        dailyLimitMinutes?.let { put("dailyLimitMinutes", it) }
        hourlyLimitMinutes?.let { put("hourlyLimitMinutes", it) }
        launchLimit?.let { put("launchLimit", it) }
        blockUntil?.let { put("blockUntil", it.toEpoch()) }
        put("schedules", JSONArray().apply {
            schedules.forEach { put(JSONObject().put("daysMask", it.daysMask).put("startMinute", it.startMinute).put("endMinute", it.endMinute)) }
        })
    }

    private fun JSONObject.toProfile(): Profile {
        val schedulesJson = optJSONArray("schedules") ?: JSONArray()
        return Profile(
            name = optString("name", "Imported"),
            enabled = optBoolean("enabled", true),
            type = runCatching { RuleType.valueOf(optString("type")) }.getOrDefault(RuleType.ALWAYS),
            packages = (optJSONArray("packages") ?: JSONArray()).toStringList().toSet(),
            wholePhone = optBoolean("wholePhone", false),
            dailyLimitMinutes = if (has("dailyLimitMinutes")) getInt("dailyLimitMinutes") else null,
            hourlyLimitMinutes = if (has("hourlyLimitMinutes")) getInt("hourlyLimitMinutes") else null,
            launchLimit = if (has("launchLimit")) getInt("launchLimit") else null,
            blockUntil = if (has("blockUntil")) getLong("blockUntil").toLocalDateTime() else null,
            schedules = buildList {
                for (i in 0 until schedulesJson.length()) {
                    val o = schedulesJson.getJSONObject(i)
                    add(Schedule(o.getInt("daysMask"), o.getInt("startMinute"), o.getInt("endMinute")))
                }
            },
        )
    }

    private fun JSONObject.stringSet(key: String) = (optJSONArray(key) ?: JSONArray()).toStringList().toSet()
    private fun JSONArray.toStringList() = (0 until length()).map { getString(it) }
    private fun LocalDateTime.toEpoch() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun Long.toLocalDateTime() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
}
