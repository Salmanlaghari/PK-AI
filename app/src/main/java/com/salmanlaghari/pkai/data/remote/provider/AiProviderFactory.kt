package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderFactory @Inject constructor(
    private val apiService: ApiService
) {
    fun getProvider(model: AiModel, usePlaceholder: Boolean = true): AiProvider {
        return if (usePlaceholder) {
            PlaceholderAiProvider(model)
        } else {
            NetworkAiProvider(model, apiService)
        }
    }
}
