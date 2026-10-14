package com.focusblock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusblock.data.SettingsStore
import com.focusblock.data.SiteLimitRepository
import com.focusblock.domain.model.SiteLimit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WebContentViewModel(
    private val settings: SettingsStore,
    siteLimits: SiteLimitRepository,
) : ViewModel() {

    /** Blocked site hosts with their stacked rules, grouped and sorted. */
    val sites: StateFlow<Map<String, List<SiteLimit>>> =
        siteLimits.limits.map { all -> all.groupBy { it.host }.toSortedMap() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val keywords: StateFlow<Set<String>?> =
        settings.blockedKeywords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Normalise a typed site to a bare host (lowercase, no scheme/path/www). */
    fun normalizeHost(raw: String): String =
        raw.trim().lowercase().substringAfter("://").substringBefore('/').removePrefix("www.")

    fun addKeyword(raw: String) {
        viewModelScope.launch {
            val keyword = raw.trim()
            if (keyword.isNotEmpty()) settings.setBlockedKeywords(settings.blockedKeywords.first() + keyword)
        }
    }

    fun removeKeyword(keyword: String) {
        viewModelScope.launch { settings.setBlockedKeywords(settings.blockedKeywords.first() - keyword) }
    }
}
