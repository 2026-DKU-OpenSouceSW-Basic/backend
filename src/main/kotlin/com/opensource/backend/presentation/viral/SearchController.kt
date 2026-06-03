package com.opensource.backend.presentation.viral

import com.opensource.backend.application.viral.SearchApplicationService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val searchApplicationService: SearchApplicationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamSearch(
        @RequestParam query: String,
        @RequestParam(defaultValue = "100") viralRange: Int
    ): SseEmitter {
        log.info("Received request to stream search for: '$query' with max viralRange: $viralRange")
        // Set timeout to 180 seconds
        val emitter = SseEmitter(180_000L)
        emitter.onTimeout { emitter.complete() }
        emitter.onError { emitter.complete() }
        
        searchApplicationService.executeStreamingSearch(query, viralRange, emitter)
        
        return emitter
    }
}
