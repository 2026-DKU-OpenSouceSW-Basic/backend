package com.opensource.backend.domain.viral.model

data class ViralScore(
    val value: Int,
    val reason: String
) {
    init {
        require(value in 0..100) { "바이럴 지수는 0에서 100 사이여야 합니다." }
    }

    companion object {
        private val SPONSOR_KEYWORDS = listOf(
            "소정의 원고료", "경제적 대가", "업체로부터 무상으로", "업체로부터 제품을",
            "대가를 제공받아", "원고료를 지원받아", "소정의 수수료", "제품을 협찬받아",
            "서비스를 제공받아", "원고료를 지급받아", "협찬을 받아", "체험단으로", "체험단에 선정"
        )

        private val EXCLUDE_KEYWORDS = listOf(
            "협찬아님", "협찬은없", "협찬이없", "협찬아니", "협찬x", "협찬X",
            "광고아님", "광고없", "광고는없", "광고가없", "광고아니", "광고x", "광고X",
            "내돈내산", "제돈제산", "내돈내먹", "직접 구매", "내 돈 주고"
        )

        fun calculate(body: String, title: String, hasSponsorshipBadge: Boolean): ViralScore {
            if (hasSponsorshipBadge) {
                return ViralScore(95, "네이버 공식 내돈내산 인증이 없거나 공식 광고 배너/컴포넌트가 감지되었습니다. (95점)")
            }

            var score = 0
            val reasons = mutableListOf<String>()

            // 1. 제목 분석
            val titleMatches = SPONSOR_KEYWORDS.filter { title.contains(it) }
            if (titleMatches.isNotEmpty()) {
                score += 40
                reasons.add("제목에 광고성 키워드 감지: [${titleMatches.joinToString(", ")}] (+40점)")
            }

            // 2. 본문 분석
            // 띄어쓰기 무관 매칭을 하기 위해 공백 제거 후 비교
            val bodyNoSpaces = body.replace("\\s".toRegex(), "")
            val titleNoSpaces = title.replace("\\s".toRegex(), "")

            val detectedSponsorKeywords = mutableListOf<String>()
            for (kw in SPONSOR_KEYWORDS) {
                val kwNoSpaces = kw.replace(" ", "")
                if (bodyNoSpaces.contains(kwNoSpaces)) {
                    // 제외 키워드가 해당 단어 근처에 있는지 확인 (단순 부정 체크)
                    val isExcluded = EXCLUDE_KEYWORDS.any { ex ->
                        val exNoSpaces = ex.replace(" ", "")
                        bodyNoSpaces.contains(exNoSpaces) && (bodyNoSpaces.contains(kwNoSpaces + exNoSpaces) || bodyNoSpaces.contains(exNoSpaces + kwNoSpaces))
                    }
                    if (!isExcluded) {
                        detectedSponsorKeywords.add(kw)
                    }
                }
            }

            if (detectedSponsorKeywords.isNotEmpty()) {
                val kwScore = (detectedSponsorKeywords.size * 25).coerceAtMost(60)
                score += kwScore
                reasons.add("본문 내 광고/협찬 문구 감지: ${detectedSponsorKeywords.take(3)} 등 ${detectedSponsorKeywords.size}개 (+${kwScore}점)")
            }

            // 3. 내돈내산 문구에 의한 감점
            val detectedExcludes = EXCLUDE_KEYWORDS.filter { bodyNoSpaces.contains(it.replace(" ", "")) || titleNoSpaces.contains(it.replace(" ", "")) }
            if (detectedExcludes.isNotEmpty()) {
                score -= 30
                reasons.add("내돈내산/비광고성 문구 감지: [${detectedExcludes.joinToString(", ")}] (-30점)")
            }

            // 점수 정규화 (0 ~ 100)
            val finalScore = score.coerceIn(0, 100)
            val finalReason = if (reasons.isEmpty()) {
                "검색 결과에서 바이럴(광고성) 징후가 검출되지 않았습니다."
            } else {
                reasons.joinToString(" | ")
            }

            return ViralScore(finalScore, finalReason)
        }
    }
}
