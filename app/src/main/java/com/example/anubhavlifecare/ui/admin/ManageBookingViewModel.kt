package com.example.anubhavlifecare.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.data.repository.AktivRepository
import com.example.anubhavlifecare.utils.SessionManager
import kotlinx.coroutines.launch

data class ManageBookingUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Admin-only actions on a booking after it has been created. The server also
 * re-checks admin rights from the logged-in user_key, so this is defence in depth.
 */
class ManageBookingViewModel(
    application: Application,
    private val aktivRepository: AktivRepository = AktivRepository(),
) : AndroidViewModel(application) {
    private val _state = MutableLiveData(ManageBookingUiState())
    val state: LiveData<ManageBookingUiState> = _state

    fun cancelBooking(billKey: Int) {
        val app = getApplication<Application>()
        if (!SessionManager.isAdmin(app)) {
            _state.value = ManageBookingUiState(error = app.getString(R.string.manage_no_permission))
            return
        }
        viewModelScope.launch {
            _state.value = ManageBookingUiState(isLoading = true)
            val userKey = SessionManager.getUserKey(app)
            aktivRepository.cancelBooking(billKey, userKey).fold(
                onSuccess = {
                    _state.value = ManageBookingUiState(
                        message = app.getString(R.string.manage_cancel_success, billKey.toString()),
                    )
                },
                onFailure = { err ->
                    _state.value = ManageBookingUiState(
                        error = err.message ?: "Could not void booking",
                    )
                },
            )
        }
    }

    fun clearMessages() {
        _state.value = _state.value?.copy(message = null, error = null)
    }
}
