package com.opensource.backend.infrastructure.crawler

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class PythonBlogAnalysisClient(
    @Value("\${python.server.url:http://localhost:8000}") private val pythonServerUrl: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 파이썬 분석 서버의 SSE를 프론트로 릴레이한다.
     * @param onCard 'card' 이벤트의 data 페이로드를 그대로 넘겨줘 호출측이 캐시에 모을 수 있게 한다.
     * @return 파이썬 스트림이 끝까지(정상) 전달됐으면 true. 중간에 끊기면 false(→ 캐시에 저장하지 않음).
     */
    fun streamAnalysis(query: String, emitter: SseEmitter, onCard: (String) -> Unit = {}): Boolean {
        log.info("Relaying stream request from Python analysis server at $pythonServerUrl for query: $query")

        var connection: HttpURLConnection? = null
        var reader: BufferedReader? = null

        try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val urlString = "$pythonServerUrl/api/v1/analyze/stream?query=$encodedQuery"
            val url = URL(urlString)

            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.readTimeout = 300_000 // 5 minutes for full crawl
            connection.connectTimeout = 15_000 // 15 seconds

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                log.error("Python analysis server returned error response: $responseCode")
                emitter.completeWithError(RuntimeException("Python server error: $responseCode"))
                return false
            }

            reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            
            var line: String?
            var currentEvent: String? = null
            
            while (reader.readLine().also { line = it } != null) {
                val trimmedLine = line!!.trim()
                
                if (trimmedLine.startsWith("event:")) {
                    currentEvent = trimmedLine.substring(6).trim()
                } else if (trimmedLine.startsWith("data:")) {
                    val dataContent = trimmedLine.substring(5).trim()
                    if (currentEvent != null) {
                        emitter.send(
                            SseEmitter.event()
                                .name(currentEvent)
                                .data(dataContent)
                        )
                        if (currentEvent == "card") onCard(dataContent)
                        log.debug("Relayed SSE Event: $currentEvent")
                    }
                } else if (trimmedLine.startsWith(":")) {
                    // 파이썬 keepalive 코멘트를 프론트로도 전달.
                    // 분석이 수십 초 걸리는 동안 침묵하면 클라이언트가 타임아웃으로 끊으므로,
                    // keepalive를 흘려보내 프론트 연결을 살려둔다.
                    emitter.send(SseEmitter.event().comment(trimmedLine.removePrefix(":").trim()))
                } else if (trimmedLine.isEmpty()) {
                    currentEvent = null
                }
            }
            
            log.info("Finished streaming relay from Python server.")
            emitter.complete()
            return true

        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (e is IllegalStateException && msg.contains("completed")) {
                // 이미 완료/타임아웃된 emitter. 여기서 completeWithError를 호출하면 Spring이
                // 에러 모델(LinkedHashMap)을 text/event-stream으로 직렬화하려다 실패하며
                // 'No converter for LinkedHashMap' 경고를 남긴다. 그냥 종료한다(finally는 실행됨).
                log.warn("SseEmitter was already completed or timed out. Stopping relay: ${e.message}")
                return false
            } else if (e.javaClass.simpleName.contains("IOException")) {
                log.warn("Network connection closed. Stopping relay: ${e.message}")
            } else {
                log.error("Error during Python stream relay", e)
            }
            try {
                emitter.completeWithError(e)
            } catch (ex: Exception) {
                // ignore
            }
        } finally {
            try {
                reader?.close()
            } catch (e: Exception) {
                // ignore
            }
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                // ignore
            }
        }
        // catch 경로(에러/조기 종료)로 빠지면 미완료이므로 false.
        return false
    }
}
