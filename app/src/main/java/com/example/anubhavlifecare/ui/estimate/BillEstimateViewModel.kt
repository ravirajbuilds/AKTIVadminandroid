package com.example.anubhavlifecare.ui.estimate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.anubhavlifecare.data.model.AktivTest
import com.example.anubhavlifecare.data.repository.AktivRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class BillEstimateUiState(
    val tests: List<AktivTest> = emptyList(),
    val selectedTests: List<AktivTest> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Backs the bill-estimate screen. Estimates are computed entirely on-device and
 * shared over WhatsApp — nothing here is written to AKTIV, so any logged-in user
 * can use it.
 */
class BillEstimateViewModel(
    application: Application,
    private val aktivRepository: AktivRepository = AktivRepository(),
) : AndroidViewModel(application) {
    private val _state = MutableLiveData(BillEstimateUiState())
    val state: LiveData<BillEstimateUiState> = _state

    private var testSearchJob: Job? = null

    init {
        searchTests("")
    }

    fun searchTests(query: String) {
        testSearchJob?.cancel()
        testSearchJob = viewModelScope.launch {
            _state.value = _state.value?.copy(isLoading = true)
            delay(300)
            aktivRepository.searchTests(query).fold(
                onSuccess = { tests ->
                    _state.value = _state.value?.copy(tests = tests, isLoading = false)
                },
                onFailure = { err ->
                    _state.value = _state.value?.copy(
                        isLoading = false,
                        error = err.message ?: "Could not load tests",
                    )
                },
            )
        }
    }

    fun toggleTest(test: AktivTest) {
        val current = _state.value?.selectedTests.orEmpty()
        val updated = if (current.any { it.testKey == test.testKey }) {
            current.filterNot { it.testKey == test.testKey }
        } else {
            current + test
        }
        _state.value = _state.value?.copy(selectedTests = updated)
    }

    fun removeTest(test: AktivTest) {
        val updated = _state.value?.selectedTests.orEmpty()
            .filterNot { it.testKey == test.testKey }
        _state.value = _state.value?.copy(selectedTests = updated)
    }

    fun clearError() {
        _state.value = _state.value?.copy(error = null)
    }
}
