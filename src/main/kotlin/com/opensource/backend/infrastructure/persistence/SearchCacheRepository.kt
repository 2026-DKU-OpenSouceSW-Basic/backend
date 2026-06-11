package com.opensource.backend.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface SearchCacheRepository : JpaRepository<SearchCacheEntity, String>
