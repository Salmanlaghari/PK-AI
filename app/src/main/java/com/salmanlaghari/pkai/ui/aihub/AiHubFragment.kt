package com.salmanlaghari.pkai.ui.aihub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.databinding.FragmentAiHubBinding
import com.salmanlaghari.pkai.databinding.ItemAiHubCardBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AiHubFragment : Fragment() {

    private var _binding: FragmentAiHubBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AiHubViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe models list flow and populate card items dynamically
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.modelsList.collect { models ->
                binding.layoutCardsContainer.removeAllViews()
                models.forEach { model ->
                    val cardBinding = ItemAiHubCardBinding.inflate(layoutInflater, binding.layoutCardsContainer, false)

                    cardBinding.tvAiLogo.text = model.emojiLogo
                    cardBinding.tvAiName.text = model.name
                    cardBinding.tvAiDescription.text = model.shortDesc
                    cardBinding.tvAiStatus.text = model.availability

                    // Set Star Favorite icon state
                    val favIconRes = if (model.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
                    cardBinding.btnFavorite.setImageResource(favIconRes)

                    // Favorite toggler action
                    cardBinding.btnFavorite.setOnClickListener {
                        viewModel.toggleFavorite(model.id)
                    }

                    // Info popup alert dialog action
                    cardBinding.btnInfo.setOnClickListener {
                        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_PkAi)
                            .setTitle("✨ ${model.name} (${model.provider})")
                            .setMessage(model.longDesc)
                            .setPositiveButton("Close") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }

                    binding.layoutCardsContainer.addView(cardBinding.root)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
