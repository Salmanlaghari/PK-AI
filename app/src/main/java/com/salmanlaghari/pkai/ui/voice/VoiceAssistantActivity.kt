package com.salmanlaghari.pkai.ui.voice

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.remote.provider.AiProviderFactory
import com.salmanlaghari.pkai.databinding.ActivityVoiceAssistantBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class VoiceAssistantActivity : AppCompatActivity() {

    @Inject
    lateinit var providerFactory: AiProviderFactory

    private lateinit var binding: ActivityVoiceAssistantBinding

    private var recognitionHelper: VoiceRecognitionHelper? = null
    private var ttsManager: TTSManager? = null

    // State management
    private enum class AssistantState {
        IDLE, LISTENING, PROCESSING, SPEAKING
    }
    private var currentState = AssistantState.IDLE

    // Active components
    private var selectedModel: AiModel = AiModel.GEMINI
    private var ttsActiveJob: Job? = null
    private var simulatedSpeechVisualizerAnimator: ValueAnimator? = null

    // Animations references
    private var concentricAnimatorSet: AnimatorSet? = null
    private var centralCoreAnimator: ObjectAnimator? = null

    // Tone feedback for state transitions
    private var toneGenerator: ToneGenerator? = null

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Force full screen flags
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        window.statusBarColor = Color.TRANSPARENT

        // Parse selected model
        val modelName = intent.getStringExtra("selected_model")
        if (modelName != null) {
            try {
                selectedModel = AiModel.valueOf(modelName)
            } catch (e: Exception) {
                selectedModel = AiModel.GEMINI
            }
        }

        binding.tvActiveModelStatus.text = "● Connected to ${selectedModel.displayName}"

        // Create tone generator for physical-feedback beeps
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (e: Exception) {
            // Ignore
        }

        setupButtons()
        setupAnimations()
        initializeTts()
    }

    private fun checkAndStartVoiceAssistant() {
        if (allPermissionsGranted()) {
            startVoiceAssistant()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (allPermissionsGranted()) {
                startVoiceAssistant()
            } else {
                Toast.makeText(this, "Microphone permission is required for Voice Assistant.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initializeTts() {
        binding.tvAnimatedStatus.text = "Initializing engines..."
        ttsManager = TTSManager(this) { success ->
            runOnUiThread {
                if (success) {
                    checkAndStartVoiceAssistant()
                } else {
                    Toast.makeText(this, "Failed to initialize TTS engine.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun startVoiceAssistant() {
        recognitionHelper = VoiceRecognitionHelper(this, object : VoiceRecognitionHelper.Callback {
            override fun onReadyForSpeech() {
                runOnUiThread {
                    setAssistantState(AssistantState.LISTENING)
                    binding.tvAnimatedStatus.text = "Listening... Speak now"
                }
            }

            override fun onBeginningOfSpeech() {
                runOnUiThread {
                    binding.tvAnimatedStatus.text = "Recording your thoughts..."
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                runOnUiThread {
                    // Normalize decibels (typically -2dB to 10dB) to a 0.0 -> 1.0 amplitude
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    binding.waveVisualizer.updateAmplitude(normalized)
                }
            }

            override fun onPartialResults(text: String) {
                runOnUiThread {
                    if (text.isNotBlank()) {
                        binding.tvAnimatedStatus.text = text
                    }
                }
            }

            override fun onResults(text: String) {
                runOnUiThread {
                    if (text.isNotBlank()) {
                        appendTranscriptBubble(text, isUser = true)
                        submitToAiModel(text)
                    } else {
                        binding.tvAnimatedStatus.text = "Try speaking again..."
                        setAssistantState(AssistantState.LISTENING)
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    binding.tvAnimatedStatus.text = error
                    // Brief delay, then retry listening safely
                    lifecycleScope.launch {
                        delay(2000)
                        if (currentState == AssistantState.LISTENING) {
                            recognitionHelper?.startListening()
                        }
                    }
                }
            }
        })

        setAssistantState(AssistantState.LISTENING)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtons() {
        // 4D Touch Animations helpers matching HomeFragment style
        val applyTouch = { view: View ->
            val scaleAnim = { scale: Float, duration: Long ->
                view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(duration)
                    .setInterpolator(LinearInterpolator())
                    .start()
            }
            view.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> scaleAnim(0.92f, 80)
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> scaleAnim(1.0f, 180)
                }
                false
            }
        }

        applyTouch(binding.btnClearTranscript)
        applyTouch(binding.btnStopToggle)
        applyTouch(binding.btnConfirmSend)

        binding.btnClearTranscript.setOnClickListener {
            binding.containerTranscriptBubbles.removeAllViews()
            binding.tvAnimatedStatus.text = "Transcript cleared."
        }

        binding.btnStopToggle.setOnClickListener {
            playToneFeedback()
            when (currentState) {
                AssistantState.LISTENING -> {
                    recognitionHelper?.stopListening()
                    binding.tvAnimatedStatus.text = "Processing voice..."
                    setAssistantState(AssistantState.PROCESSING)
                }
                AssistantState.SPEAKING -> {
                    ttsManager?.stop()
                    setAssistantState(AssistantState.LISTENING)
                }
                else -> {
                    setAssistantState(AssistantState.LISTENING)
                }
            }
        }

        // Long press center button exits voice assistant activity
        binding.btnStopToggle.setOnLongClickListener {
            finish()
            true
        }

        binding.btnConfirmSend.setOnClickListener {
            // Find all user transcripts to return
            val lastUserText = getCombinedUserTranscript()
            if (lastUserText.isNotBlank()) {
                val resultIntent = Intent()
                resultIntent.putExtra("confirmed_transcript", lastUserText)
                setResult(Activity.RESULT_OK, resultIntent)
            }
            finish()
        }
    }

    private fun getCombinedUserTranscript(): String {
        val count = binding.containerTranscriptBubbles.childCount
        val userTexts = ArrayList<String>()
        for (i in 0 until count) {
            val child = binding.containerTranscriptBubbles.getChildAt(i)
            val isUser = child.tag as? Boolean ?: false
            if (isUser) {
                val tv = child.findViewById<TextView>(R.id.tv_user_message)
                    ?: child.findViewById<TextView>(R.id.tv_ai_message)
                    ?: child.findViewById<TextView>(R.id.tv_chat_message)
                if (tv != null) {
                    userTexts.add(tv.text.toString())
                }
            }
        }
        return userTexts.joinToString(" ")
    }

    private fun playToneFeedback() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun setAssistantState(state: AssistantState) {
        currentState = state
        when (state) {
            AssistantState.LISTENING -> {
                binding.tvAssistantStatusLabel.text = "VOICE ASSISTANT · LISTENING"
                binding.tvAssistantStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.electric_blue_glow))
                binding.coreGradientBg.setBackgroundResource(R.drawable.bg_core_listening)
                binding.ivMicIcon.setImageResource(R.drawable.ic_mic)

                concentricAnimatorSet?.start()
                recognitionHelper?.startListening()

                stopSpeechSimulatedVisualizer()
            }
            AssistantState.PROCESSING -> {
                binding.tvAssistantStatusLabel.text = "VOICE ASSISTANT · PROCESSING"
                binding.tvAssistantStatusLabel.setTextColor(Color.parseColor("#FFA500"))
                binding.coreGradientBg.setBackgroundResource(R.drawable.bg_core_speaking)
                binding.ivMicIcon.setImageResource(R.drawable.ic_check)

                concentricAnimatorSet?.cancel()
                recognitionHelper?.stopListening()

                stopSpeechSimulatedVisualizer()
            }
            AssistantState.SPEAKING -> {
                binding.tvAssistantStatusLabel.text = "VOICE ASSISTANT · AI SPEAKING"
                binding.tvAssistantStatusLabel.setTextColor(Color.parseColor("#A800FF"))
                binding.coreGradientBg.setBackgroundResource(R.drawable.bg_core_speaking)
                binding.ivMicIcon.setImageResource(R.drawable.ic_share) // Represents speak

                concentricAnimatorSet?.start()
                recognitionHelper?.stopListening()

                startSpeechSimulatedVisualizer()
            }
            AssistantState.IDLE -> {
                binding.tvAssistantStatusLabel.text = "VOICE ASSISTANT · IDLE"
                binding.tvAssistantStatusLabel.setTextColor(Color.GRAY)
                binding.coreGradientBg.setBackgroundResource(R.drawable.bg_pulse_ring)

                concentricAnimatorSet?.cancel()
                recognitionHelper?.cancel()

                stopSpeechSimulatedVisualizer()
            }
        }
    }

    private fun submitToAiModel(prompt: String) {
        setAssistantState(AssistantState.PROCESSING)
        binding.tvAnimatedStatus.text = "Thinking..."

        lifecycleScope.launch {
            try {
                val provider = withContext(Dispatchers.IO) {
                    providerFactory.getProvider(selectedModel)
                }
                // Generate completion safely
                val aiResponse = withContext(Dispatchers.IO) {
                    provider.generateResponse(prompt)
                }

                if (aiResponse.isNotBlank()) {
                    appendTranscriptBubble(aiResponse, isUser = false)
                    speakResponse(aiResponse)
                } else {
                    setAssistantState(AssistantState.LISTENING)
                }
            } catch (e: Exception) {
                binding.tvAnimatedStatus.text = "Error: ${e.localizedMessage}"
                setAssistantState(AssistantState.LISTENING)
            }
        }
    }

    private fun speakResponse(text: String) {
        setAssistantState(AssistantState.SPEAKING)

        // Simulate typing transcript effect
        binding.tvAnimatedStatus.text = ""
        ttsActiveJob?.cancel()
        ttsActiveJob = lifecycleScope.launch {
            val words = text.split(" ")
            val sb = java.lang.StringBuilder()
            for (word in words) {
                sb.append(word).append(" ")
                binding.tvAnimatedStatus.text = sb.toString()
                delay(80) // 80ms word streaming rate
            }
        }

        // Read out response with TextToSpeech
        ttsManager?.speak(text) {
            runOnUiThread {
                setAssistantState(AssistantState.LISTENING)
            }
        }
    }

    private fun appendTranscriptBubble(text: String, isUser: Boolean) {
        val inflater = LayoutInflater.from(this)
        val bubbleLayoutId = if (isUser) {
            // Simplified custom styled user bubble
            R.layout.item_chat_user
        } else {
            // Simplified custom styled AI bubble
            R.layout.item_chat_ai
        }

        val bubbleView = inflater.inflate(bubbleLayoutId, binding.containerTranscriptBubbles, false)
        bubbleView.tag = isUser

        val tv = bubbleView.findViewById<TextView>(R.id.tv_user_message)
            ?: bubbleView.findViewById<TextView>(R.id.tv_ai_message)
            ?: bubbleView.findViewById<TextView>(R.id.tv_chat_message)
        if (tv != null) {
            tv.text = text
        }

        // Layout parameters customization for the transcript look
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
            topMargin = 12
            bottomMargin = 12
        }
        bubbleView.layoutParams = params

        binding.containerTranscriptBubbles.addView(bubbleView)

        // Smooth auto scroll to the bottom of the transcript scrollView
        binding.scrollTranscript.post {
            binding.scrollTranscript.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun setupAnimations() {
        // Concluding Rings concentric pulse animation
        val ring1ScaleX = ObjectAnimator.ofFloat(binding.pulseRing1, "scaleX", 1f, 1.6f)
        val ring1ScaleY = ObjectAnimator.ofFloat(binding.pulseRing1, "scaleY", 1f, 1.6f)
        val ring1Alpha = ObjectAnimator.ofFloat(binding.pulseRing1, "alpha", 1f, 0f)

        val ring2ScaleX = ObjectAnimator.ofFloat(binding.pulseRing2, "scaleX", 1f, 1.8f)
        val ring2ScaleY = ObjectAnimator.ofFloat(binding.pulseRing2, "scaleY", 1f, 1.8f)
        val ring2Alpha = ObjectAnimator.ofFloat(binding.pulseRing2, "alpha", 1f, 0f)

        val ring3ScaleX = ObjectAnimator.ofFloat(binding.pulseRing3, "scaleX", 1f, 2.0f)
        val ring3ScaleY = ObjectAnimator.ofFloat(binding.pulseRing3, "scaleY", 1f, 2.0f)
        val ring3Alpha = ObjectAnimator.ofFloat(binding.pulseRing3, "alpha", 1f, 0f)

        concentricAnimatorSet = AnimatorSet().apply {
            playTogether(
                ring1ScaleX, ring1ScaleY, ring1Alpha,
                ring2ScaleX, ring2ScaleY, ring2Alpha,
                ring3ScaleX, ring3ScaleY, ring3Alpha
            )
            duration = 2500
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Repeat concentric pulse infinitely with loop listener
        concentricAnimatorSet?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (currentState == AssistantState.LISTENING || currentState == AssistantState.SPEAKING) {
                    concentricAnimatorSet?.start()
                }
            }
        })

        // Central Core micro-scale pulse
        val coreScaleX = ObjectAnimator.ofFloat(binding.centralCoreSphere, "scaleX", 1f, 1.08f, 1f)
        val coreScaleY = ObjectAnimator.ofFloat(binding.centralCoreSphere, "scaleY", 1f, 1.08f, 1f)
        centralCoreAnimator = ObjectAnimator.ofFloat(binding.centralCoreSphere, "rotation", 0f, 360f).apply {
            duration = 10000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
        centralCoreAnimator?.start()
    }

    private fun startSpeechSimulatedVisualizer() {
        simulatedSpeechVisualizerAnimator?.cancel()
        // Simulate wavy speech amplitude shifts at 60fps
        simulatedSpeechVisualizerAnimator = ValueAnimator.ofFloat(0.1f, 0.7f).apply {
            duration = 350
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val waveVal = animator.animatedValue as Float
                binding.waveVisualizer.updateAmplitude(waveVal)
            }
            start()
        }
    }

    private fun stopSpeechSimulatedVisualizer() {
        simulatedSpeechVisualizerAnimator?.cancel()
        binding.waveVisualizer.updateAmplitude(0f)
    }

    override fun onPause() {
        super.onPause()
        recognitionHelper?.stopListening()
        ttsManager?.stop()
        stopSpeechSimulatedVisualizer()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognitionHelper?.destroy()
        recognitionHelper = null

        ttsManager?.shutdown()
        ttsManager = null

        toneGenerator?.release()
        toneGenerator = null

        concentricAnimatorSet?.cancel()
        centralCoreAnimator?.cancel()
        stopSpeechSimulatedVisualizer()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Smooth transition exit activity
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
