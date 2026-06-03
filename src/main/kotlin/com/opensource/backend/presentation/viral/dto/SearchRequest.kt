package com.opensource.backend.presentation.viral.dto

data class SearchRequest(
    val query: String,
    val viralRange: Int = 100
)
