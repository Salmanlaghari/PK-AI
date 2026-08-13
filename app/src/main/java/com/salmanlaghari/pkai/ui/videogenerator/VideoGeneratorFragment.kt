package com.salmanlaghari.pkai.ui.videogenerator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.databinding.FragmentVideoGeneratorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VideoGeneratorFragment : Fragment() {

    private var _binding: FragmentVideoGeneratorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VideoGeneratorViewModel by viewModels()

    private var selectedDurationSeconds = 5

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoGeneratorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnGenerate.setOnClickListener {
            viewModel.generate(binding.etPrompt.text.toString().trim(), selectedDurationSeconds)
        }

        // Duration selector: 5s / 10s / 15s
        binding.btnDuration5.isChecked = true
        binding.durationGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedDurationSeconds = when (checkedId) {
                    binding.btnDuration15.id -> 15
                    binding.btnDuration10.id -> 10
                    else -> 5
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    VideoGenUiState.Idle -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.tvStatus.visibility = View.GONE
                        binding.btnGenerate.isEnabled = true
                    }
                    VideoGenUiState.Generating -> {
                        binding.layoutLoading.visibility = View.VISIBLE
                        binding.tvStatus.visibility = View.GONE
                        binding.btnGenerate.isEnabled = false
                    }
                    is VideoGenUiState.Success -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.tvStatus.text = state.message
                        binding.tvStatus.visibility = View.VISIBLE
                        binding.btnGenerate.isEnabled = true
                    }
                    is VideoGenUiState.Error -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.tvStatus.text = state.message
                        binding.tvStatus.visibility = View.VISIBLE
                        binding.btnGenerate.isEnabled = true
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}