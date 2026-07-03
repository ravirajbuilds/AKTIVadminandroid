package com.example.anubhavlifecare.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.utils.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ManageBookingFragment : Fragment() {
    private lateinit var viewModel: ManageBookingViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_manage_booking, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application),
        )[ManageBookingViewModel::class.java]

        val tvWarning = view.findViewById<TextView>(R.id.tvManageWarning)
        val etBillKey = view.findViewById<TextInputEditText>(R.id.etBillKey)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelBooking)
        val progress = view.findViewById<ProgressBar>(R.id.progressManage)

        val isAdmin = SessionManager.isAdmin(requireContext())
        if (!isAdmin) {
            tvWarning.visibility = View.VISIBLE
            etBillKey.isEnabled = false
            btnCancel.isEnabled = false
        }

        btnCancel.setOnClickListener {
            val billKey = etBillKey.text?.toString()?.trim()?.toIntOrNull()
            if (billKey == null) {
                Toast.makeText(requireContext(), R.string.manage_need_bill_key, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            viewModel.cancelBooking(billKey)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            btnCancel.isEnabled = isAdmin && !state.isLoading
            state.message?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                etBillKey.text?.clear()
                viewModel.clearMessages()
            }
            state.error?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }
    }
}
