package com.example.anubhavlifecare.utils

import android.app.Activity
import android.util.Log
import org.json.JSONObject

class PaymentManager(private val activity: Activity) {

    companion object {
        private const val TAG = "PaymentManager"

        // This will be replaced with BuildConfig.RAZORPAY_KEY_ID when dependencies are loaded
        private const val RAZORPAY_KEY_ID = "rzp_live_RFl7SbGT4LwcLq"
    }

    fun startPayment(
        amount: Double,
        name: String,
        email: String,
        phone: String,
        description: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            // This is a placeholder implementation
            // Will be replaced with actual Razorpay integration once dependencies are loaded

            Log.d(TAG, "Payment initiated for amount: ₹$amount")
            Log.d(TAG, "Customer: $name, $email, $phone")
            Log.d(TAG, "Description: $description")

            // Simulate success for now
            onSuccess("temp_payment_id_${System.currentTimeMillis()}")

        } catch (e: Exception) {
            Log.e(TAG, "Payment failed", e)
            onFailure(e.message ?: "Payment failed")
        }
    }

    private fun createPaymentOptions(
        amount: Double,
        name: String,
        email: String,
        phone: String,
        description: String
    ): JSONObject {
        val options = JSONObject()

        try {
            options.put("name", "Anubhav Life Care")
            options.put("description", description)
            options.put("image", "") // Add clinic logo URL here
            options.put("order_id", "") // Generate order ID from backend
            options.put("theme.color", "#3399cc")
            options.put("currency", "INR")
            options.put("amount", (amount * 100).toInt()) // Convert to paise
            options.put("key", RAZORPAY_KEY_ID)

            val prefill = JSONObject()
            prefill.put("email", email)
            prefill.put("contact", phone)
            prefill.put("name", name)

            options.put("prefill", prefill)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating payment options", e)
        }

        return options
    }
}