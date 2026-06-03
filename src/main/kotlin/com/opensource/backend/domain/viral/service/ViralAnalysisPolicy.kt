package com.opensource.backend.domain.viral.service

import com.opensource.backend.domain.viral.model.BlogDocument
import com.opensource.backend.domain.viral.model.ViralScore
import org.springframework.stereotype.Service

@Service
class ViralAnalysisPolicy {
    fun calculateScore(document: BlogDocument): ViralScore {
        return ViralScore(document.viralScore, document.reason)
    }
}
