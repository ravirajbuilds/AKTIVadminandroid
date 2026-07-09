package com.example.anubhavlifecare.data.remote

import com.example.anubhavlifecare.data.model.AktivLoginRequest
import com.example.anubhavlifecare.data.model.AktivLoginResponse
import com.example.anubhavlifecare.data.model.AktivBillNumber
import com.example.anubhavlifecare.data.model.AktivBookingRequest
import com.example.anubhavlifecare.data.model.AktivBookingResponse
import com.example.anubhavlifecare.data.model.AktivCollectionCentre
import com.example.anubhavlifecare.data.model.AktivDoctor
import com.example.anubhavlifecare.data.model.AktivReceptionUser
import com.example.anubhavlifecare.data.model.AktivTest
import com.example.anubhavlifecare.data.model.CategoryRevenue
import com.example.anubhavlifecare.data.model.CentreRevenue
import com.example.anubhavlifecare.data.model.DayRevenue
import com.example.anubhavlifecare.data.model.DoctorRevenue
import com.example.anubhavlifecare.data.model.SalesSummary
import com.example.anubhavlifecare.data.model.YoyResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AktivApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: AktivLoginRequest): AktivLoginResponse

    @GET("api/users")
    suspend fun listUsers(): List<AktivReceptionUser>

    @GET("api/tests")
    suspend fun searchTests(
        @Query("q") query: String = "",
        @Query("limit") limit: Int = 50,
    ): List<AktivTest>

    @GET("api/doctors")
    suspend fun searchDoctors(
        @Query("q") query: String = "",
        @Query("limit") limit: Int = 50,
    ): List<AktivDoctor>

    @GET("api/collection-centres")
    suspend fun listCollectionCentres(
        @Query("q") query: String = "",
    ): List<AktivCollectionCentre>

    @GET("api/next-bill-number")
    suspend fun nextBillNumber(
        @Query("bill_date") billDate: String? = null,
        @Query("test_mode") testMode: Boolean? = null,
    ): AktivBillNumber

    @POST("api/bookings")
    suspend fun pushBooking(@Body request: AktivBookingRequest): AktivBookingResponse

    @POST("api/bookings/{billKey}/cancel")
    suspend fun cancelBooking(
        @Path("billKey") billKey: Int,
        @Body body: Map<String, Int?> = emptyMap(),
    ): Map<String, Any>

    // ---- Sales analytics (server 403s if the user_key lacks can_view_sales) ----
    @GET("api/analytics/summary")
    suspend fun salesSummary(@Query("user_key") userKey: Int, @Query("date_from") from: String? = null, @Query("date_to") to: String? = null): SalesSummary

    @GET("api/analytics/by-day")
    suspend fun salesByDay(@Query("user_key") userKey: Int, @Query("date_from") from: String? = null, @Query("date_to") to: String? = null): List<DayRevenue>

    @GET("api/analytics/by-category")
    suspend fun salesByCategory(@Query("user_key") userKey: Int, @Query("date_from") from: String? = null, @Query("date_to") to: String? = null): List<CategoryRevenue>

    @GET("api/analytics/by-doctor")
    suspend fun salesByDoctor(@Query("user_key") userKey: Int, @Query("date_from") from: String? = null, @Query("date_to") to: String? = null): List<DoctorRevenue>

    @GET("api/analytics/by-centre")
    suspend fun salesByCentre(@Query("user_key") userKey: Int, @Query("date_from") from: String? = null, @Query("date_to") to: String? = null): List<CentreRevenue>

    @GET("api/analytics/yoy")
    suspend fun salesYoy(@Query("user_key") userKey: Int): YoyResponse
}
