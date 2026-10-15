package dev.sushil.otium.domain

/**
 * Decides whether the text in a browser's address bar is a blocked site or search.
 * Pure Kotlin so it can be unit tested. The text may be a URL, a bare host, or a
 * search phrase typed into the address bar.
 */
object SiteMatcher {

    /** A short reason if [barText] is blocked, else null. */
    fun blockedReason(barText: String, sites: Set<String>, keywords: Set<String>): String? {
        val text = barText.trim().lowercase()
        if (text.isEmpty()) return null

        val host = extractHost(text)?.removePrefix("www.")
        if (host != null) {
            for (raw in sites) {
                val site = raw.trim().lowercase().removePrefix("www.").ifEmpty { continue }
                // "instagram.com" blocks instagram.com and m.instagram.com, not notinstagram.com
                if (host == site || host.endsWith(".$site")) return "Blocked site: $site"
            }
        }
        for (raw in keywords) {
            val keyword = raw.trim().lowercase()
            if (keyword.isNotEmpty() && text.contains(keyword)) return "Blocked keyword: ${raw.trim()}"
        }
        return null
    }

    /** The host part of a URL/host string, or null if [text] looks like a search phrase. */
    fun extractHost(text: String): String? {
        val afterScheme = if ("://" in text) text.substringAfter("://") else text
        val host = afterScheme.substringBefore('/').substringBefore('?').trim()
        // A host has a dot and no spaces; "best pizza near me" is a search, not a host
        return if ('.' in host && ' ' !in host) host else null
    }
}
