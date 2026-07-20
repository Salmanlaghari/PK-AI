package com.salmanlaghari.pkai.data.model

data class AiHubModel(
    val id: String,
    val name: String,
    val provider: String,
    val emojiLogo: String,
    val shortDesc: String,
    val longDesc: String,
    val availability: String,
    var isFavorite: Boolean = false
)
