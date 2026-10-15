package dev.sushil.otium.data

import android.content.Context
import org.json.JSONObject

/** One in-app feed we can close, e.g. YouTube Shorts. */
data class FeedSection(val packageName: String, val viewId: String, val settingKey: String)

/** Loads feed sections (sections.json) and the adult-site seed list (adult_domains.txt). */
class ContentRegistry(context: Context) {

    val sections: List<FeedSection> = runCatching {
        val obj = JSONObject(context.assets.open("sections.json").bufferedReader().use { it.readText() })
        buildList {
            obj.keys().forEach { pkg ->
                if (pkg.startsWith("_")) return@forEach
                val o = obj.getJSONObject(pkg)
                add(FeedSection(pkg, o.getString("viewId"), o.getString("key")))
            }
        }
    }.getOrDefault(emptyList())

    val sectionPackages: Set<String> = sections.mapTo(mutableSetOf()) { it.packageName }

    val adultDomains: Set<String> = runCatching {
        context.assets.open("adult_domains.txt").bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        }
    }.getOrDefault(emptySet())

    val adultKeywords: Set<String> = setOf("porn", "xxx", "nsfw")
}
