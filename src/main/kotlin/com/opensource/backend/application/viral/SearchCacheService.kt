package com.opensource.backend.application.viral

import com.opensource.backend.infrastructure.persistence.SearchCacheEntity
import com.opensource.backend.infrastructure.persistence.SearchCacheRepository
import com.opensource.backend.infrastructure.persistence.SearchKeywordStatEntity
import com.opensource.backend.infrastructure.persistence.SearchKeywordStatRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 검색어 단위 결과 캐시 + 검색어 빈도 집계를 담당.
 * 각 메서드가 독립 트랜잭션이며, 비동기 릴레이 스레드에서 호출된다.
 */
@Service
class SearchCacheService(
    private val searchCacheRepository: SearchCacheRepository,
    private val keywordStatRepository: SearchKeywordStatRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${viral.cache.ttl-hours:6}") private val ttlHours: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 검색어 검색 횟수 +1 (없으면 생성). 동시성 안전. */
    @Transactional
    fun recordSearch(query: String) {
        val key = query.trim()
        if (key.isEmpty()) return
        val updated = keywordStatRepository.incrementCount(key, Instant.now())
        if (updated == 0) {
            // 행이 없으면 신규 생성. 드물게 동시 삽입이 충돌할 수 있으나
            // 검색어 집계는 best-effort라 호출측(executeStreamingSearch)에서 예외를 무시한다.
            keywordStatRepository.save(SearchKeywordStatEntity(key, 1, Instant.now()))
        }
    }

    /** 유효한(만료되지 않은) 캐시가 있으면 카드 JSON 목록을 반환, 없으면 null. */
    @Transactional(readOnly = true)
    fun findFreshCards(query: String): List<String>? {
        val entity = searchCacheRepository.findById(query.trim()).orElse(null) ?: return null
        if (entity.expiresAt.isBefore(Instant.now())) return null
        return objectMapper.readValue(entity.cardsJson, Array<String>::class.java).toList()
    }

    /** 분석 완료된 카드 목록을 캐시에 저장(upsert), TTL 갱신. */
    @Transactional
    fun saveCards(query: String, cards: List<String>) {
        if (cards.isEmpty()) return
        val key = query.trim()
        val json = objectMapper.writeValueAsString(cards)
        val now = Instant.now()
        val expires = now.plus(ttlHours, ChronoUnit.HOURS)

        val existing = searchCacheRepository.findById(key).orElse(null)
        if (existing == null) {
            searchCacheRepository.save(SearchCacheEntity(key, json, now, expires))
        } else {
            existing.cardsJson = json
            existing.createdAt = now
            existing.expiresAt = expires // 변경 감지로 커밋 시 반영
        }
        log.info("Cached {} cards for query '{}' (expires in {}h).", cards.size, key, ttlHours)
    }

    /** 인기 검색어 상위 N개(검색 횟수 내림차순). */
    @Transactional(readOnly = true)
    fun popularKeywords(limit: Int): List<String> =
        keywordStatRepository
            .findAllByOrderBySearchCountDesc(PageRequest.of(0, limit.coerceIn(1, 100)))
            .map { it.keyword }
}
