package com.opensource.backend.application.viral.dto

data class SearchStepResponse(
    val step: Int,
    val message: String
)

data class SearchResultCardResponse(
    val title: String,
    val link: String,
    val viralScore: Int,
    val reason: String
)
