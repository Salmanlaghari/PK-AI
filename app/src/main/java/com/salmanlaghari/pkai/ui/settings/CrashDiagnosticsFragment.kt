package com.salmanlaghari.pkai.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.databinding.FragmentCrashDiagnosticsBinding
import com.salmanlaghari.pkai.util.CrashDiagnosticsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CrashDiagnosticsFragment : Fragment() {

    private var _binding: FragmentCrashDiagnosticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CrashDiagnosticsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCrashDiagnosticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        lifecycleScope.launch {
            viewModel.crashLog.collect { log ->
                binding.tvCrashLog.text = log ?: getString(R.string.no_crash_log_available)
                binding.tvCrashTimestamp.visibility = if (log != null) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.crashTimestamp.collect { timestamp ->
                binding.tvCrashTimestamp.text = getString(R.string.crash_timestamp_format, timestamp ?: "")
            }
        }

        binding.btnClear.setOnClickListener {
            viewModel.clearCrashLog()
            Toast.makeText(requireContext(), R.string.crash_log_cleared, Toast.LENGTH_SHORT).show()
        }

        binding.btnCopy.setOnClickListener {
            val log = viewModel.crashLog.value
            if (!log.isNullOrEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Crash Log", log)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnShare.setOnClickListener {
            val log = viewModel.crashLog.value
            if (!log.isNullOrEmpty()) {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, log)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share_crash_log)))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
