package com.opensource.backend.infrastructure.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SearchKeywordStatRepository : JpaRepository<SearchKeywordStatEntity, String> {

    /** 검색 횟수가 많은 순으로 인기 검색어 조회(상위 N개는 Pageable로 제한). */
    fun findAllByOrderBySearchCountDesc(pageable: Pageable): List<SearchKeywordStatEntity>

    /**
     * 동시 요청에서도 카운트 유실이 없도록 원자적 증가로 처리.
     * 반환값 0이면 해당 키워드 행이 아직 없다는 뜻(→ 신규 insert).
     */
    @Modifying
    @Query(
        "UPDATE SearchKeywordStatEntity s " +
            "SET s.searchCount = s.searchCount + 1, s.lastSearchedAt = :now " +
            "WHERE s.keyword = :keyword"
    )
    fun incrementCount(@Param("keyword") keyword: String, @Param("now") now: Instant): Int
}
