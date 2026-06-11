package com.opensource.backend.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 검색어 단위 분석 결과 캐시.
 * cardsJson: 파이썬이 SSE로 보낸 card 이벤트의 data 페이로드(JSON 문자열)들을 JSON 배열로 직렬화해 저장.
 * expiresAt: 이 시각 이후면 만료로 간주해 다시 분석한다(TTL).
 */
@Entity
@Table(name = "search_cache")
class SearchCacheEntity(
    @Id
    @Column(length = 255)
    val query: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    var cardsJson: String,

    @Column(nullable = false)
    var createdAt: Instant,

    @Column(nullable = false)
    var expiresAt: Instant,
)
