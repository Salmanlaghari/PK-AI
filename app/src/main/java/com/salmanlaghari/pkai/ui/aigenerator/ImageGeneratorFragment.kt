package com.salmanlaghari.pkai.ui.aigenerator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.databinding.FragmentImageGeneratorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImageGeneratorFragment : Fragment() {

    private var _binding: FragmentImageGeneratorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ImageGeneratorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageGeneratorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnGenerate.setOnClickListener {
            viewModel.generate(binding.etPrompt.text.toString().trim())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    ImageGenUiState.Idle -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.ivResult.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                        binding.btnGenerate.isEnabled = true
                    }
                    ImageGenUiState.Loading -> {
                        binding.layoutLoading.visibility = View.VISIBLE
                        binding.ivResult.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                        binding.btnGenerate.isEnabled = false
                    }
                    is ImageGenUiState.Success -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                        binding.ivResult.setImageBitmap(state.bitmap)
                        binding.ivResult.visibility = View.VISIBLE
                        binding.btnGenerate.isEnabled = true
                    }
                    is ImageGenUiState.Error -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.ivResult.visibility = View.GONE
                        binding.tvError.text = state.message
                        binding.tvError.visibility = View.VISIBLE
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