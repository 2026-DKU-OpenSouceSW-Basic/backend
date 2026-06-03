package com.opensource.backend.application.viral

import com.opensource.backend.infrastructure.crawler.PythonBlogAnalysisClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class SearchApplicationService(
    private val pythonBlogAnalysisClient: PythonBlogAnalysisClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun executeStreamingSearch(query: String, viralRange: Int, emitter: SseEmitter) {
        log.info("Starting async search stream relay for query: '$query'")
        pythonBlogAnalysisClient.streamAnalysis(query, emitter)
    }
}
