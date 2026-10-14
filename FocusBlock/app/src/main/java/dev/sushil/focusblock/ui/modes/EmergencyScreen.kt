package dev.sushil.focusblock.ui.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sushil.focusblock.admin.FocusAdminReceiver
import dev.sushil.focusblock.data.ModeStore
import dev.sushil.focusblock.domain.EmergencyUnlock
import dev.sushil.focusblock.domain.model.EmergencyState
import dev.sushil.focusblock.domain.model.Mode
import dev.sushil.focusblock.domain.model.ModeState
import dev.sushil.focusblock.service.ModeKeeper
import dev.sushil.focusblock.ui.common.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmergencyViewModel(private val store: ModeStore) : ViewModel() {

    val state: StateFlow<ModeState?> = store.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var challenge by mutableStateOf(EmergencyUnlock.newChallenge())
        private set
    var typed by mutableStateOf("")
        private set
    var hint by mutableStateOf<String?>(null)
        private set

    /** One character at a time, exactly right, or it starts over with a new string. */
    fun onType(raw: String) {
        val text = raw.uppercase().filter { !it.isWhitespace() }
        hint = null
        when {
            text.length > typed.length + 1 -> restart("Pasting isn't allowed. Start again with the new text.")
            !challenge.startsWith(text) -> restart("Typo. Start again with the new text.")
            else -> {
                typed = text
                if (typed == challenge) start()
            }
        }
    }

    private fun restart(message: String) {
        typed = ""
        challenge = EmergencyUnlock.newChallenge()
        hint = message
    }

    private fun start() {
        viewModelScope.launch {
            store.update { it.copy(emergency = EmergencyUnlock.start(it.emergency, it.mode, System.currentTimeMillis())) }
        }
        typed = ""
    }

    fun cancel() {
        viewModelScope.launch { store.update { it.copy(emergency = EmergencyUnlock.cancel(it.emergency)) } }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            store.update {
                if (it.emergency.phase != EmergencyState.Phase.WINDOW) it
                else ModeKeeper.leaveToNormal(it.copy(emergency = EmergencyUnlock.complete(it.emergency, System.currentTimeMillis())))
            }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(onClose: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val vm: EmergencyViewModel = viewModel(factory = viewModelFactory { initializer { EmergencyViewModel(container.modeStore) } })
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency unlock") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Back") } },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (s.mode == Mode.NORMAL) {
                Text("You're in Normal mode, so there's nothing to unlock.")
                return@Column
            }
            when (s.emergency.phase) {
                EmergencyState.Phase.IDLE -> {
                    val wait = EmergencyUnlock.waitFor(s.mode, s.emergency.history, System.currentTimeMillis())
                    Text(
                        "This is the slow way out of ${if (s.mode == Mode.STRICT) "Strict" else "Lock"} mode. " +
                            "Type the text below exactly. Then wait ${formatHours(wait)} of phone-on time (time with the phone off " +
                            "doesn't count). Your rules stay on while you wait. Each use in the last 30 days doubles the next wait.",
                    )
                    Text(
                        vm.challenge.chunked(5).joinToString(" "),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedTextField(
                        value = vm.typed.chunked(5).joinToString(" "),
                        onValueChange = vm::onType,
                        label = { Text("${vm.typed.length} / ${vm.challenge.length}") },
                        supportingText = vm.hint?.let { { Text(it) } },
                        isError = vm.hint != null,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.None,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                EmergencyState.Phase.WAITING -> {
                    Text("Waiting", style = MaterialTheme.typography.headlineSmall)
                    Text("${formatHours(s.emergency.remainingMs)} of phone-on time left. Updated every 30 seconds.")
                    OutlinedButton(onClick = vm::cancel) { Text("Cancel emergency unlock") }
                }
                EmergencyState.Phase.WINDOW -> {
                    Text("Ready", style = MaterialTheme.typography.headlineSmall)
                    Text("You have ${formatHours(s.emergency.remainingMs)} to use this. If you miss it, you start over.")
                    Button(
                        onClick = {
                            vm.finish(onClose)
                            FocusAdminReceiver.deactivate(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Switch to Normal mode") }
                }
            }
        }
    }
}
