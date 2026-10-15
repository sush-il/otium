package dev.sushil.otium.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sushil.otium.data.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FeedsViewModel(private val settings: SettingsStore) : ViewModel() {

    private fun Flow<Boolean>.state(): StateFlow<Boolean?> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val youtubeShorts = settings.blockYoutubeShorts.state()
    val instagramReels = settings.blockInstagramReels.state()
    val facebookReels = settings.blockFacebookReels.state()
    val adult = settings.blockAdult.state()

    fun setYoutubeShorts(v: Boolean) = viewModelScope.launch { settings.setBlockYoutubeShorts(v) }.let {}
    fun setInstagramReels(v: Boolean) = viewModelScope.launch { settings.setBlockInstagramReels(v) }.let {}
    fun setFacebookReels(v: Boolean) = viewModelScope.launch { settings.setBlockFacebookReels(v) }.let {}
    fun setAdult(v: Boolean) = viewModelScope.launch { settings.setBlockAdult(v) }.let {}
}
