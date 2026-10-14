package dev.sushil.focusblock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sushil.focusblock.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WebContentViewModel(private val settings: SettingsStore) : ViewModel() {

    val sites: StateFlow<Set<String>?> =
        settings.blockedSites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val keywords: StateFlow<Set<String>?> =
        settings.blockedKeywords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Sites are normalised (lowercase, no scheme/path) so matching is predictable
    fun addSite(raw: String) = edit {
        val site = raw.trim().lowercase().substringAfter("://").substringBefore('/').removePrefix("www.")
        if (site.isNotEmpty()) settings.setBlockedSites(settings.blockedSites.first() + site)
    }

    fun removeSite(site: String) = edit { settings.setBlockedSites(settings.blockedSites.first() - site) }

    fun addKeyword(raw: String) = edit {
        val keyword = raw.trim()
        if (keyword.isNotEmpty()) settings.setBlockedKeywords(settings.blockedKeywords.first() + keyword)
    }

    fun removeKeyword(keyword: String) = edit { settings.setBlockedKeywords(settings.blockedKeywords.first() - keyword) }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
