package com.salmanlaghari.pkai.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fail-closed image-generation boundary.
 *
 * The app currently has no way to classify a generated image before displaying
 * it. Prompt keywords and a provider URL flag are not enough to ensure output
 * safety, so remote image generation remains disabled until output moderation
 * can be applied and tested.
 */
@Singleton
class PollinationsImageRepository @Inject constructor() : ImageGenerationProvider {
    override suspend fun generateImage(
        prompt: String,
        width: Int,
        height: Int
    ): ImageGenerationResult = ImageGenerationResult.Blocked(
        "AI image generation is temporarily unavailable while image-safety checks are added."
    )
}
