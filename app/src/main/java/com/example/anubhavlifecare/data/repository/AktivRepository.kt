package com.example.anubhavlifecare.data.repository

import com.example.anubhavlifecare.data.model.AktivBillNumber
import com.example.anubhavlifecare.data.model.AktivBookingRequest
import com.example.anubhavlifecare.data.model.AktivBookingResponse
import com.example.anubhavlifecare.data.model.AktivCollectionCentre
import com.example.anubhavlifecare.data.model.AktivDoctor
import com.example.anubhavlifecare.data.model.AktivTest
import com.example.anubhavlifecare.data.remote.AktivApiClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AktivRepository(
    private val api: com.example.anubhavlifecare.data.remote.AktivApi = AktivApiClient.api,
) {
    suspend fun searchTests(query: String): Result<List<AktivTest>> = runCatching {
        api.searchTests(query = query, limit = 50)
    }

    suspend fun searchDoctors(query: String): Result<List<AktivDoctor>> = runCatching {
        api.searchDoctors(query = query, limit = 50)
    }

    suspend fun listCollectionCentres(query: String = ""): Result<List<AktivCollectionCentre>> =
        runCatching {
            api.listCollectionCentres(query)
        }

    suspend fun getNextBillNumber(billDate: LocalDate? = null): Result<AktivBillNumber> =
        runCatching {
            val dateStr = billDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
            api.nextBillNumber(billDate = dateStr)
        }

    /**
     * Push booked patient data into AKTIV (Bill + appointment).
     * [apntDate] is sent as today's date on the server automatically.
     */
    suspend fun pushBookingToAktiv(request: AktivBookingRequest): Result<AktivBookingResponse> =
        runCatching {
            api.pushBooking(request)
        }

    suspend fun pushBookingToAktiv(
        patientName: String,
        phone: String,
        sex: String,
        ageYear: Int?,
        ageMonth: Int? = null,
        ageDay: Int? = null,
        refrdoctorKey: Int?,
        collcentreKey: Int = 1,
        testKeys: List<Int>,
        billDate: LocalDate? = null,
        billNumber: String? = null,
        amountPaid: Double? = null,
        receiptMode: String = "CASH",
        chequeNo: String? = null,
        remarks: String? = null,
    ): Result<AktivBookingResponse> = pushBookingToAktiv(
        AktivBookingRequest(
            patientName = patientName,
            phone = phone,
            sex = sex,
            ageYear = ageYear,
            ageMonth = ageMonth,
            ageDay = ageDay,
            refrdoctorKey = refrdoctorKey,
            collcentreKey = collcentreKey,
            testKeys = testKeys,
            billDate = billDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            billNumber = billNumber,
            amountPaid = amountPaid,
            receiptMode = receiptMode,
            chequeNo = chequeNo,
            remarks = remarks,
        ),
    )
}
