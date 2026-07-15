package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.AiModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderFactory @Inject constructor() {
    fun getProvider(model: AiModel): AiProvider {
        return PlaceholderAiProvider(model)
    }
}
