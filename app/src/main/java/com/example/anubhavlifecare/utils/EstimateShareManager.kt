package com.example.anubhavlifecare.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import com.example.anubhavlifecare.R
import java.io.File
import java.io.FileOutputStream

/** A single test line on a bill estimate. */
data class EstimateLine(val name: String, val amount: Double)

/** Everything needed to render / share one bill estimate. */
data class EstimateData(
    val patientName: String,
    val phone: String,
    val dateText: String,
    val lines: List<EstimateLine>,
    val subtotal: Double,
    val discountLabel: String,
    val discountAmount: Double,
    val total: Double,
)

/**
 * Renders a bill estimate to an image and shares it (photo or text) on WhatsApp.
 * Any logged-in user can produce an estimate — it never touches AKTIV.
 */
object EstimateShareManager {
    private const val RENDER_WIDTH_PX = 1080
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /** Trim trailing ".0" so ₹300 shows as 300 and ₹12.5 stays 12.5. */
    fun formatAmount(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format("%.2f", value)

    fun buildMessage(data: EstimateData): String = buildString {
        append("*ANUBHAV LIFE CARE*\n")
        append("Bill Estimate\n")
        if (data.patientName.isNotBlank()) append("Patient: ${data.patientName}\n")
        append("Date: ${data.dateText}\n")
        append("--------------------------------\n")
        data.lines.forEachIndexed { index, line ->
            append("${index + 1}. ${line.name} — ₹${formatAmount(line.amount)}\n")
        }
        append("--------------------------------\n")
        append("Subtotal: ₹${formatAmount(data.subtotal)}\n")
        if (data.discountAmount > 0) {
            append("${data.discountLabel}: -₹${formatAmount(data.discountAmount)}\n")
        }
        append("*Total Payable: ₹${formatAmount(data.total)}*\n")
        append("\n_This is only an estimate, not a tax invoice._")
    }

    /** Render the estimate card to a bitmap suitable for sending as a photo. */
    fun renderToBitmap(context: Context, data: EstimateData): Bitmap {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.view_estimate_receipt, null, false)
        bind(context, view, data)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(RENDER_WIDTH_PX, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(
            view.measuredWidth,
            view.measuredHeight,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return bitmap
    }

    /** Render the estimate to an image and share it — prefers WhatsApp, falls back to a chooser. */
    fun shareAsImage(context: Context, data: EstimateData): Boolean {
        val file = saveBitmap(context, renderToBitmap(context, data))
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}$FILE_PROVIDER_SUFFIX",
            file,
        )
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, buildMessage(data))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                context.startActivity(Intent(base).setPackage(pkg))
                return true
            } catch (_: ActivityNotFoundException) {
                // WhatsApp flavour not installed — try the next one.
            }
        }
        return try {
            context.startActivity(Intent.createChooser(base, "Share Bill Estimate"))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Send the breakdown as a WhatsApp text message (to a specific number if one is given). */
    fun shareAsText(context: Context, data: EstimateData): Boolean {
        val message = buildMessage(data)
        val normalized = normalizePhone(data.phone)
        if (normalized.isNotBlank()) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://wa.me/$normalized?text=${Uri.encode(message)}")
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // No wa.me handler — fall through to a plain share.
            }
        }

        val base = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        return try {
            context.startActivity(Intent(base).setPackage("com.whatsapp"))
            true
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent.createChooser(base, "Send Bill Estimate"))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }

    /** Reduce a typed phone to WhatsApp's country-code form (defaults to India +91). */
    private fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91${digits.drop(1)}"
            else -> digits
        }
    }

    private fun bind(context: Context, view: View, data: EstimateData) {
        view.findViewById<TextView>(R.id.tvEstimateDate).text = "Date: ${data.dateText}"

        val tvPatient = view.findViewById<TextView>(R.id.tvEstimatePatient)
        val identity = listOf(data.patientName, data.phone).filter { it.isNotBlank() }
        if (identity.isEmpty()) {
            tvPatient.visibility = View.GONE
        } else {
            tvPatient.visibility = View.VISIBLE
            tvPatient.text = "Patient: ${identity.joinToString("  •  ")}"
        }

        val container = view.findViewById<LinearLayout>(R.id.estimateItemsContainer)
        container.removeAllViews()
        data.lines.forEachIndexed { index, line ->
            container.addView(
                itemRow(context, "${index + 1}. ${line.name}", "₹${formatAmount(line.amount)}"),
            )
        }

        view.findViewById<TextView>(R.id.tvEstimateSubtotal).text =
            "₹${formatAmount(data.subtotal)}"

        val discountRow = view.findViewById<View>(R.id.rowEstimateDiscount)
        if (data.discountAmount > 0) {
            discountRow.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvEstimateDiscountLabel).text = data.discountLabel
            view.findViewById<TextView>(R.id.tvEstimateDiscount).text =
                "-₹${formatAmount(data.discountAmount)}"
        } else {
            discountRow.visibility = View.GONE
        }

        view.findViewById<TextView>(R.id.tvEstimateTotal).text = "₹${formatAmount(data.total)}"
    }

    private fun itemRow(context: Context, name: String, amount: String): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(0, 14, 0, 14)
        }
        row.addView(
            TextView(context).apply {
                text = name
                textSize = 16f
                setTextColor(0xFF212121.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                )
            },
        )
        row.addView(
            TextView(context).apply {
                text = amount
                textSize = 16f
                setTextColor(0xFF212121.toInt())
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            },
        )
        return row
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "estimates").apply { mkdirs() }
        val file = File(dir, "bill_estimate.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
