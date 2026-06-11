package com.opensource.backend.application.viral

import com.opensource.backend.infrastructure.crawler.PythonBlogAnalysisClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class SearchApplicationService(
    private val pythonBlogAnalysisClient: PythonBlogAnalysisClient,
    private val searchCacheService: SearchCacheService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun executeStreamingSearch(query: String, viralRange: Int, emitter: SseEmitter) {
        log.info("Starting async search stream relay for query: '$query'")

        // 검색어 빈도 집계(인기 검색어 산출용). 부가 기능이므로 실패해도 검색은 계속 진행.
        try {
            searchCacheService.recordSearch(query)
        } catch (e: Exception) {
            log.warn("recordSearch failed (non-fatal): {}", e.message)
        }

        // 1) 캐시 HIT → 파이썬 호출 없이 저장된 카드를 즉시 전송
        val cached = searchCacheService.findFreshCards(query)
        if (cached != null) {
            log.info("Cache HIT for query '{}' ({} cards). Skipping Python.", query, cached.size)
            replayFromCache(emitter, cached)
            return
        }

        // 2) 캐시 MISS → 기존대로 파이썬 릴레이하되, card 페이로드를 모았다가 완료 시 캐싱
        log.info("Cache MISS for query '{}'. Relaying from Python.", query)
        val collected = mutableListOf<String>()
        val completed = pythonBlogAnalysisClient.streamAnalysis(query, emitter) { card ->
            collected.add(card)
        }

        // 끝까지 정상 전달된 경우에만 저장(중간에 끊긴 부분 결과는 캐싱하지 않음)
        if (completed && collected.isNotEmpty()) {
            searchCacheService.saveCards(query, collected)
        }
    }

    /** 인기 검색어 상위 N개. */
    fun popularKeywords(limit: Int): List<String> = searchCacheService.popularKeywords(limit)

    private fun replayFromCache(emitter: SseEmitter, cards: List<String>) {
        try {
            emitter.send(
                SseEmitter.event().name("progress")
                    .data("{\"step\":1,\"message\":\"캐시에서 불러오는 중...\"}")
            )
            for (card in cards) {
                emitter.send(SseEmitter.event().name("card").data(card))
            }
            emitter.send(
                SseEmitter.event().name("progress")
                    .data("{\"step\":3,\"message\":\"완료(캐시)\"}")
            )
            emitter.complete()
        } catch (e: Exception) {
            log.warn("Failed to replay cached cards: {}", e.message)
            try {
                emitter.complete()
            } catch (ex: Exception) {
                // ignore
            }
        }
    }
}
