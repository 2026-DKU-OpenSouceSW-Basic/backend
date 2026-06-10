package com.opensource.backend.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 검색어별 검색 횟수 집계. '자주 검색되는 검색어'(인기 검색어) 산출과
 * 인기 검색어 사전 캐싱(pre-warm)의 근거 데이터로 쓴다.
 */
@Entity
@Table(name = "search_keyword_stat")
class SearchKeywordStatEntity(
    @Id
    @Column(length = 255)
    val keyword: String,

    @Column(nullable = false)
    var searchCount: Long = 0,

    @Column(nullable = false)
    var lastSearchedAt: Instant,
)
