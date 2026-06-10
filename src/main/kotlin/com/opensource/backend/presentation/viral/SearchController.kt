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
        // 블로그 20개 × 포스트당 수십 초(스크래핑+OCR+추론) → 전체 수십 분이 걸린다.
        // 고정 타임아웃(기존 180초)은 카드 2~3장만 받고 끊기는 직접 원인이므로 타임아웃을 끈다(0L = 무제한).
        // 파이썬이 멈추는 경우는 릴레이의 readTimeout(5분 무음)이 안전망으로 처리한다.
        val emitter = SseEmitter(0L)
        emitter.onTimeout { emitter.complete() }
        emitter.onError { emitter.complete() }
        
        searchApplicationService.executeStreamingSearch(query, viralRange, emitter)

        return emitter
    }

    /** 자주 검색되는 검색어(인기 검색어) 상위 N개. */
    @GetMapping("/popular")
    fun popularKeywords(@RequestParam(defaultValue = "10") limit: Int): List<String> =
        searchApplicationService.popularKeywords(limit)
}
