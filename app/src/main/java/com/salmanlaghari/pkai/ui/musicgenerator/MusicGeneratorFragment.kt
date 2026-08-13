package com.salmanlaghari.pkai.ui.musicgenerator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.databinding.FragmentMusicGeneratorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicGeneratorFragment : Fragment() {

    private var _binding: FragmentMusicGeneratorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicGeneratorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicGeneratorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnGenerate.setOnClickListener {
            val style = when {
                binding.btnStyleHiphop.isChecked -> "Hip-Hop"
                binding.btnStyleElectronic.isChecked -> "Electronic"
                binding.btnStyleClassical.isChecked -> "Classical"
                else -> "Pop"
            }
            viewModel.generate(binding.etPrompt.text.toString().trim(), style)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    MusicGenUiState.Idle -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.tvStatus.visibility = View.GONE
                        binding.btnGenerate.isEnabled = true
                    }
                    MusicGenUiState.Generating -> {
                        binding.layoutLoading.visibility = View.VISIBLE
                        binding.tvStatus.visibility = View.GONE
                        binding.btnGenerate.isEnabled = false
                    }
                    is MusicGenUiState.Success -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.tvStatus.text = state.message
                        binding.tvStatus.visibility = View.VISIBLE
                        binding.btnGenerate.isEnabled = true
                    }
                    is MusicGenUiState.Error -> {
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