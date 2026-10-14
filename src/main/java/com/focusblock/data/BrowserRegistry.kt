package com.focusblock.data

import android.content.Context
import org.json.JSONObject

/** Loads the browser -> address-bar view-id map from assets/browsers.json, once. */
class BrowserRegistry(context: Context) {

    private val urlBarIds: Map<String, String> = runCatching {
        val json = context.assets.open("browsers.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        buildMap {
            obj.keys().forEach { key ->
                if (!key.startsWith("_")) put(key, obj.getString(key))
            }
        }
    }.getOrDefault(emptyMap())

    val browserPackages: Set<String> = urlBarIds.keys

    fun isBrowser(pkg: String) = pkg in urlBarIds

    fun urlBarViewId(pkg: String): String? = urlBarIds[pkg]
}
