package com.example.anubhavlifecare.ui.booking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhavlifecare.data.model.AktivBillNumber
import com.example.anubhavlifecare.data.model.AktivBookingResponse
import com.example.anubhavlifecare.data.model.AktivCollectionCentre
import com.example.anubhavlifecare.data.model.AktivDoctor
import com.example.anubhavlifecare.data.model.AktivTest
import com.example.anubhavlifecare.data.repository.AktivRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BookTestUiState(
    val billNumber: AktivBillNumber? = null,
    val billDate: LocalDate = LocalDate.now(),
    val tests: List<AktivTest> = emptyList(),
    val doctors: List<AktivDoctor> = emptyList(),
    val collectionCentres: List<AktivCollectionCentre> = emptyList(),
    val selectedTests: List<AktivTest> = emptyList(),
    val selectedDoctor: AktivDoctor? = null,
    val selectedCentre: AktivCollectionCentre? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: AktivBookingResponse? = null,
)

class BookTestViewModel(
    private val aktivRepository: AktivRepository = AktivRepository(),
) : ViewModel() {
    private val _state = MutableLiveData(BookTestUiState())
    val state: LiveData<BookTestUiState> = _state

    private var doctorSearchJob: Job? = null
    private var testSearchJob: Job? = null

    init {
        loadMasters()
    }

    fun loadMasters() {
        viewModelScope.launch {
            _state.value = _state.value?.copy(isLoading = true, error = null)
            val bill = aktivRepository.getNextBillNumber().getOrNull()
            val centres = aktivRepository.listCollectionCentres().getOrElse { emptyList() }
            val defaultCentre = centres.firstOrNull { it.collcentreName == "ANUBHAV LIFE CARE" }
                ?: centres.firstOrNull()
            _state.value = _state.value?.copy(
                isLoading = false,
                billNumber = bill,
                collectionCentres = centres,
                selectedCentre = defaultCentre,
            )
        }
    }

    fun searchTests(query: String) {
        testSearchJob?.cancel()
        testSearchJob = viewModelScope.launch {
            delay(300)
            val tests = aktivRepository.searchTests(query).getOrElse { emptyList() }
            _state.value = _state.value?.copy(tests = tests)
        }
    }

    fun searchDoctors(query: String) {
        doctorSearchJob?.cancel()
        doctorSearchJob = viewModelScope.launch {
            delay(300)
            val doctors = aktivRepository.searchDoctors(query).getOrElse { emptyList() }
            _state.value = _state.value?.copy(doctors = doctors)
        }
    }

    fun selectDoctor(doctor: AktivDoctor?) {
        _state.value = _state.value?.copy(selectedDoctor = doctor)
    }

    fun selectCentre(centre: AktivCollectionCentre?) {
        _state.value = _state.value?.copy(selectedCentre = centre)
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

    fun submitBooking(
        patientName: String,
        phone: String,
        sex: String,
        ageYear: Int?,
        ageMonth: Int?,
        ageDay: Int?,
        amountPaid: Double?,
        receiptMode: String,
        chequeNo: String?,
        remarks: String?,
    ) {
        val current = _state.value ?: return
        if (patientName.isBlank() || phone.isBlank()) {
            _state.value = current.copy(error = "Name and phone are required")
            return
        }
        if (current.selectedTests.isEmpty()) {
            _state.value = current.copy(error = "Select at least one test")
            return
        }

        viewModelScope.launch {
            _state.value = current.copy(isLoading = true, error = null, success = null)
            val result = aktivRepository.pushBookingToAktiv(
                patientName = patientName.trim(),
                phone = phone.trim(),
                sex = sex,
                ageYear = ageYear,
                ageMonth = ageMonth,
                ageDay = ageDay,
                refrdoctorKey = current.selectedDoctor?.refrdoctorKey,
                collcentreKey = current.selectedCentre?.collcentreKey ?: 1,
                testKeys = current.selectedTests.map { it.testKey },
                billDate = current.billDate,
                billNumber = current.billNumber?.billNumber,
                amountPaid = amountPaid ?: current.selectedTests.sumOf { it.rate },
                receiptMode = receiptMode,
                chequeNo = chequeNo,
                remarks = remarks,
            )
            result.fold(
                onSuccess = { response ->
                    _state.value = _state.value?.copy(isLoading = false, success = response)
                    loadMasters()
                },
                onFailure = { err ->
                    _state.value = _state.value?.copy(
                        isLoading = false,
                        error = err.message ?: "Booking failed",
                    )
                },
            )
        }
    }

    fun clearMessages() {
        _state.value = _state.value?.copy(error = null, success = null)
    }
}
