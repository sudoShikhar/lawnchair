package app.lawnchair.backup.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.backup.NovaBackupConverter
import app.lawnchair.backup.NovaBackupInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RestoreNovaBackupUiState {
    val isLoading: Boolean

    data class Success(val converter: NovaBackupConverter, val info: NovaBackupInfo) : RestoreNovaBackupUiState {
        override val isLoading: Boolean = false
    }

    data object Loading : RestoreNovaBackupUiState {
        override val isLoading: Boolean = true
    }

    data object Error : RestoreNovaBackupUiState {
        override val isLoading: Boolean = true
    }
}

private data class RestoreNovaBackupViewModelState(
    val converter: NovaBackupConverter? = null,
    val info: NovaBackupInfo? = null,
    val hasError: Boolean = false,
) {
    fun toUiState(): RestoreNovaBackupUiState = when {
        hasError -> RestoreNovaBackupUiState.Error
        converter != null && info != null -> RestoreNovaBackupUiState.Success(converter, info)
        else -> RestoreNovaBackupUiState.Loading
    }
}

class RestoreNovaBackupViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private var initialized = false

    private val viewModelState = MutableStateFlow(RestoreNovaBackupViewModelState())
    val uiState = viewModelState
        .map { it.toUiState() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            viewModelState.value.toUiState(),
        )

    fun init(backupUri: Uri) {
        if (initialized) return
        initialized = true
        val converter = NovaBackupConverter(getApplication(), backupUri)
        viewModelScope.launch {
            try {
                val info = converter.parseInfo()
                viewModelState.update { it.copy(converter = converter, info = info) }
            } catch (t: Throwable) {
                Log.e("RestoreNovaBackupViewModel", "failed to parse Nova backup", t)
                viewModelState.update { it.copy(hasError = true) }
            }
        }
    }
}
