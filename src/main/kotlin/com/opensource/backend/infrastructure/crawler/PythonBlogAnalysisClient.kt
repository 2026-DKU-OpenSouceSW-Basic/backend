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

    fun streamAnalysis(query: String, emitter: SseEmitter) {
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
                return
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
                        log.debug("Relayed SSE Event: $currentEvent")
                    }
                } else if (trimmedLine.isEmpty()) {
                    currentEvent = null
                }
            }
            
            log.info("Finished streaming relay from Python server.")
            emitter.complete()

        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (e is IllegalStateException && msg.contains("completed")) {
                log.warn("SseEmitter was already completed or timed out. Stopping relay: ${e.message}")
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
    }
}
