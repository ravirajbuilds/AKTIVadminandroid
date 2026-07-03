package com.example.anubhavlifecare.data.remote

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object AktivApiClient {
    /**
     * Clinic LAN API — run `uvicorn main:app --host 0.0.0.0 --port 8080` in api/.
     * Override in build.gradle: buildConfigField("String", "AKTIV_API_URL", "\"http://...\"")
     */
    const val DEFAULT_BASE_URL = "http://192.168.29.157:8080/"

    private val gson = GsonBuilder().setLenient().create()

    /** Read a String field from the generated BuildConfig, or null if absent. */
    private fun buildConfigString(field: String): String? = try {
        Class.forName("com.example.anubhavlifecare.BuildConfig")
            .getField(field)
            .get(null) as? String
    } catch (_: Exception) {
        null
    }

    private val isDebug: Boolean by lazy {
        try {
            Class.forName("com.example.anubhavlifecare.BuildConfig")
                .getField("DEBUG")
                .getBoolean(null)
        } catch (_: Exception) {
            false
        }
    }

    private val apiKey: String by lazy { buildConfigString("AKTIV_API_KEY").orEmpty() }

    private val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        // Attach the shared secret when one is configured for this build.
        val key = apiKey
        if (key.isNotBlank()) {
            builder.addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-API-Key", key)
                    .build()
                chain.proceed(request)
            }
        }

        // Full request/response bodies contain patient PII and login credentials,
        // so only log them in debug builds — never in a release APK.
        val logging = HttpLoggingInterceptor().apply {
            level = if (isDebug) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        builder.addInterceptor(logging)

        builder.build()
    }

    val api: AktivApi by lazy {
        val baseUrl = buildConfigString("AKTIV_API_URL") ?: DEFAULT_BASE_URL

        Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AktivApi::class.java)
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
