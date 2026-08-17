package com.salmanlaghari.pkai.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.databinding.FragmentSplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SplashViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Branded "PK AI / Premium Assist" launch animation (non-blocking, lightweight)
        binding.ivSplashLogo.apply {
            scaleX = 0.6f
            scaleY = 0.6f
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(900)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        binding.tvSplashTitle.apply {
            alpha = 0f
            animate().alpha(1f).setDuration(1100).setStartDelay(200).start()
        }
        binding.tvSplashSubtitle.apply {
            alpha = 0f
            animate().alpha(1f).setDuration(1100).setStartDelay(400).start()
        }

        lifecycleScope.launch {
            delay(1800) // short branded intro

            val session = viewModel.userSessionFlow.first()
            if (session.isLoggedIn) {
                // Returning user — go straight to the app
                findNavController().navigate(R.id.action_splashFragment_to_homeFragment)
            } else {
                // First launch — show the auth screen (Continue with Google / Continue as Guest)
                findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
