package com.example.anubhavlifecare.ui.estimate

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.data.model.AktivTest
import com.example.anubhavlifecare.utils.EstimateShareManager
import com.example.anubhavlifecare.utils.EstimateShareManager.formatAmount
import com.example.anubhavlifecare.utils.EstimateData
import com.example.anubhavlifecare.utils.EstimateLine
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillEstimateFragment : Fragment() {
    private lateinit var viewModel: BillEstimateViewModel
    private lateinit var searchAdapter: EstimateSearchAdapter
    private lateinit var selectedAdapter: EstimateSelectedAdapter

    private lateinit var etName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etDiscount: TextInputEditText
    private lateinit var spinnerDiscountType: AutoCompleteTextView
    private lateinit var tvSummary: TextView
    private lateinit var tvEmpty: TextView

    private val flatOption get() = getString(R.string.estimate_discount_flat)
    private val percentOption get() = getString(R.string.estimate_discount_percent)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_bill_estimate, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application),
        )[BillEstimateViewModel::class.java]

        etName = view.findViewById(R.id.etEstimateName)
        etPhone = view.findViewById(R.id.etEstimatePhone)
        etDiscount = view.findViewById(R.id.etEstimateDiscount)
        spinnerDiscountType = view.findViewById(R.id.spinnerDiscountType)
        tvSummary = view.findViewById(R.id.tvEstimateSummary)
        tvEmpty = view.findViewById(R.id.tvEstimateEmpty)
        val etSearch = view.findViewById<TextInputEditText>(R.id.etEstimateSearch)
        val rvSearch = view.findViewById<RecyclerView>(R.id.rvEstimateSearch)
        val rvSelected = view.findViewById<RecyclerView>(R.id.rvEstimateSelected)
        val btnShareImage = view.findViewById<MaterialButton>(R.id.btnShareImage)
        val btnShareText = view.findViewById<MaterialButton>(R.id.btnShareText)

        spinnerDiscountType.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                listOf(flatOption, percentOption),
            ),
        )
        spinnerDiscountType.setText(flatOption, false)
        spinnerDiscountType.setOnItemClickListener { _, _, _, _ -> refreshSummary() }

        searchAdapter = EstimateSearchAdapter { test -> viewModel.toggleTest(test) }
        rvSearch.layoutManager = LinearLayoutManager(requireContext())
        rvSearch.adapter = searchAdapter

        selectedAdapter = EstimateSelectedAdapter { test -> viewModel.removeTest(test) }
        rvSelected.layoutManager = LinearLayoutManager(requireContext())
        rvSelected.adapter = selectedAdapter

        etSearch.addTextChangedListener(simpleWatcher { viewModel.searchTests(it) })
        etDiscount.addTextChangedListener(simpleWatcher { refreshSummary() })

        btnShareImage.setOnClickListener {
            val estimate = buildEstimate() ?: return@setOnClickListener
            if (!EstimateShareManager.shareAsImage(requireContext(), estimate)) {
                Toast.makeText(requireContext(), R.string.estimate_no_app, Toast.LENGTH_LONG).show()
            }
        }
        btnShareText.setOnClickListener {
            val estimate = buildEstimate() ?: return@setOnClickListener
            if (!EstimateShareManager.shareAsText(requireContext(), estimate)) {
                Toast.makeText(requireContext(), R.string.estimate_no_app, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            searchAdapter.submit(state.tests, state.selectedTests)
            selectedAdapter.submit(state.selectedTests)
            tvEmpty.visibility = if (state.selectedTests.isEmpty()) View.VISIBLE else View.GONE
            refreshSummary()
            state.error?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    /** Build the estimate from the current selection, or show a toast and return null if empty. */
    private fun buildEstimate(): EstimateData? {
        val selected = viewModel.state.value?.selectedTests.orEmpty()
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.estimate_need_test, Toast.LENGTH_SHORT).show()
            return null
        }
        val subtotal = selected.sumOf { it.rate }
        val discountValue = etDiscount.text?.toString()?.toDoubleOrNull() ?: 0.0
        val isPercent = spinnerDiscountType.text?.toString() == percentOption
        val discountAmount = (if (isPercent) subtotal * discountValue / 100.0 else discountValue)
            .coerceIn(0.0, subtotal)
        val discountLabel = if (isPercent && discountValue > 0) {
            "Discount (${formatAmount(discountValue)}%)"
        } else {
            "Discount"
        }
        val total = (subtotal - discountAmount).coerceAtLeast(0.0)
        val dateText = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        return EstimateData(
            patientName = etName.text?.toString()?.trim().orEmpty(),
            phone = etPhone.text?.toString()?.trim().orEmpty(),
            dateText = dateText,
            lines = selected.map { EstimateLine(it.testName, it.rate) },
            subtotal = subtotal,
            discountLabel = discountLabel,
            discountAmount = discountAmount,
            total = total,
        )
    }

    private fun refreshSummary() {
        val estimate = buildEstimateForPreview()
        if (estimate == null) {
            tvSummary.text = getString(R.string.estimate_empty)
            return
        }
        tvSummary.text = buildString {
            append("Subtotal: ₹${formatAmount(estimate.subtotal)}")
            if (estimate.discountAmount > 0) {
                append("\n${estimate.discountLabel}: -₹${formatAmount(estimate.discountAmount)}")
            }
            append("\nTotal Payable: ₹${formatAmount(estimate.total)}")
        }
    }

    /** Same maths as [buildEstimate] but silent — used to refresh the on-screen summary. */
    private fun buildEstimateForPreview(): EstimateData? {
        val selected = viewModel.state.value?.selectedTests.orEmpty()
        if (selected.isEmpty()) return null
        val subtotal = selected.sumOf { it.rate }
        val discountValue = etDiscount.text?.toString()?.toDoubleOrNull() ?: 0.0
        val isPercent = spinnerDiscountType.text?.toString() == percentOption
        val discountAmount = (if (isPercent) subtotal * discountValue / 100.0 else discountValue)
            .coerceIn(0.0, subtotal)
        val discountLabel = if (isPercent && discountValue > 0) {
            "Discount (${formatAmount(discountValue)}%)"
        } else {
            "Discount"
        }
        val total = (subtotal - discountAmount).coerceAtLeast(0.0)
        return EstimateData(
            patientName = "",
            phone = "",
            dateText = "",
            lines = selected.map { EstimateLine(it.testName, it.rate) },
            subtotal = subtotal,
            discountLabel = discountLabel,
            discountAmount = discountAmount,
            total = total,
        )
    }

    private fun simpleWatcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
            onChange(s?.toString().orEmpty())
        override fun afterTextChanged(s: Editable?) = Unit
    }
}

private class EstimateSearchAdapter(
    private val onToggle: (AktivTest) -> Unit,
) : RecyclerView.Adapter<EstimateSearchAdapter.VH>() {
    private var items: List<AktivTest> = emptyList()
    private var selected: Set<Int> = emptySet()

    fun submit(tests: List<AktivTest>, selectedTests: List<AktivTest>) {
        items = tests
        selected = selectedTests.map { it.testKey }.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply { setPadding(24, 24, 24, 24) }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val test = items[position]
        val checked = selected.contains(test.testKey)
        holder.text.text = "${if (checked) "✓ " else "+ "}${test.testName} (₹${test.rate})"
        holder.text.setBackgroundColor(if (checked) 0xFFE3F2FD.toInt() else 0x00000000)
        holder.text.setOnClickListener { onToggle(test) }
    }

    override fun getItemCount(): Int = items.size

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)
}

private class EstimateSelectedAdapter(
    private val onRemove: (AktivTest) -> Unit,
) : RecyclerView.Adapter<EstimateSelectedAdapter.VH>() {
    private var items: List<AktivTest> = emptyList()

    fun submit(selectedTests: List<AktivTest>) {
        items = selectedTests
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply { setPadding(24, 20, 24, 20) }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val test = items[position]
        holder.text.text = "${test.testName}   ₹${test.rate}    ✕"
        holder.text.setOnClickListener { onRemove(test) }
    }

    override fun getItemCount(): Int = items.size

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)
}
