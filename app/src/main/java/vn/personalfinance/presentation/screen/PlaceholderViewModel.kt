package vn.personalfinance.presentation.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PlaceholderUiState(val isLoading: Boolean = false)

@HiltViewModel
class PlaceholderViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(PlaceholderUiState())
    val uiState: StateFlow<PlaceholderUiState> = _uiState.asStateFlow()
}
