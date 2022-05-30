package dev.goncalomartins.push.handler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.goncalomartins.push.service.MessagingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

/**
 * Subscribe SSE Handler
 *
 * @property messagingService API to subscribe to messages
 * @constructor Create Subscribe SSE Handler
 */
@Component
class SubscribeSSEHandler(
    private val messagingService: MessagingService
) {

    /**
     * Logger
     */
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * Object mapper
     */
    private val objectMapper = jacksonObjectMapper()

    /**
     * Subscribe to Messages
     *
     * @param serverRequest represents the server-side HTTP request
     * @return a [Mono] that emits the [ServerResponse]
     */
    fun subscribe(
        serverRequest: ServerRequest
    ): Mono<ServerResponse> = Mono.just(serverRequest.queryParam("channels"))
        .flatMap { optChannels ->
            val channels = optChannels.orElseThrow {
                IllegalArgumentException("'channels' query parameter required")
            }.split(",")

            ServerResponse
                .ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(
                    messages(*channels.toTypedArray())
                        .doOnSubscribe {
                            logger.info("[SSE] Connection has been established")
                        }
                        .doFinally {
                            logger.info("[SSE] Connection has been terminated")
                        },
                    ServerSentEvent::class.java
                )
        }.onErrorResume(IllegalArgumentException::class.java) {
            ServerResponse.badRequest().bodyValue(mapOf("error" to it.message))
        }
        .onErrorResume {
            logger.error("[SSE] Connection had an error: '{}'", it.message, it)
            ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).bodyValue(mapOf("error" to it.message))
        }

    /**
     * Messages
     *
     * @param channels the channels to subscribe to
     */
    private fun messages(vararg channels: String) = messagingService
        .subscribe(*channels)
        .map { message ->
            logger.info("[SSE] Pushing message from channel '{}'", message.channel)
            ServerSentEvent.builder<String>()
                .event("message")
                .data(objectMapper.writeValueAsString(message))
                .build()
        }
}
