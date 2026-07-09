package com.example.anubhavlifecare.data.repository

import com.example.anubhavlifecare.data.model.AktivLoginRequest
import com.example.anubhavlifecare.data.model.AktivLoginResponse
import com.example.anubhavlifecare.data.model.AktivBillNumber
import com.example.anubhavlifecare.data.model.AktivBookingRequest
import com.example.anubhavlifecare.data.model.AktivBookingResponse
import com.example.anubhavlifecare.data.model.AktivCollectionCentre
import com.example.anubhavlifecare.data.model.AktivDoctor
import com.example.anubhavlifecare.data.model.AktivReceptionUser
import com.example.anubhavlifecare.data.model.AktivTest
import com.example.anubhavlifecare.data.remote.AktivApiClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AktivRepository(
    private val api: com.example.anubhavlifecare.data.remote.AktivApi = AktivApiClient.api,
) {
    suspend fun login(userid: String, password: String): Result<AktivLoginResponse> =
        runCatching {
            api.login(AktivLoginRequest(userid, password))
        }

    // ---- Sales analytics (only for users whose role grants can_view_sales) ----
    suspend fun salesSummary(userKey: Int, from: String? = null, to: String? = null) =
        runCatching { api.salesSummary(userKey, from, to) }
    suspend fun salesByDay(userKey: Int, from: String? = null, to: String? = null) =
        runCatching { api.salesByDay(userKey, from, to) }
    suspend fun salesByCategory(userKey: Int, from: String? = null, to: String? = null) =
        runCatching { api.salesByCategory(userKey, from, to) }
    suspend fun salesByDoctor(userKey: Int, from: String? = null, to: String? = null) =
        runCatching { api.salesByDoctor(userKey, from, to) }
    suspend fun salesByCentre(userKey: Int, from: String? = null, to: String? = null) =
        runCatching { api.salesByCentre(userKey, from, to) }
    suspend fun salesYoy(userKey: Int) = runCatching { api.salesYoy(userKey) }

    suspend fun listReceptionUsers(): Result<List<AktivReceptionUser>> = runCatching {
        api.listUsers()
    }

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

    suspend fun getNextBillNumber(
        billDate: LocalDate? = null,
        testMode: Boolean? = null,
    ): Result<AktivBillNumber> = runCatching {
        val dateStr = billDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        api.nextBillNumber(billDate = dateStr, testMode = testMode)
    }

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
        testMode: Boolean? = null,
        sysUserKey: Int? = null,
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
            testMode = testMode,
            sysUserKey = sysUserKey,
        ),
    )

    /** Void receipt only — never deletes the AKTIV bill row. */
    suspend fun cancelBooking(billKey: Int, sysUserKey: Int? = null): Result<Boolean> =
        runCatching {
            val body = if (sysUserKey != null) mapOf("sys_user_key" to sysUserKey) else emptyMap()
            api.cancelBooking(billKey, body)
            true
        }
}
