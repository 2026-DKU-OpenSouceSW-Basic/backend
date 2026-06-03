package com.opensource.backend.domain.viral.model

data class BlogDocument(
    val title: String,
    val link: String,
    val body: String,
    val bloggerName: String,
    val postDate: String,
    val viralScore: Int,
    val reason: String
)
