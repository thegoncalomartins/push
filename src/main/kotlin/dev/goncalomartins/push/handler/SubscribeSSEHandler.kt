package dev.goncalomartins.push.handler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.goncalomartins.push.service.MessagingService
import dev.goncalomartins.push.utils.HandlerUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

/**
 * Subscribe SSE Handler
 *
 * @property messagingService API to subscribe to messages
 * @constructor Create Subscribe SSE Handler
 */
@Component
class SubscribeSSEHandler(
    @Value("\${push.reconnect.dither.min.duration}")
    private val minReconnectDitherDuration: Duration,
    @Value("\${push.reconnect.dither.max.duration}")
    private val maxReconnectDitherDuration: Duration,
    @Value("\${push.client.close.grace.period.duration}")
    private val clientCloseGracePeriodDuration: Duration,
    @Value("\${push.heartbeat.interval.duration}")
    private val heartbeatIntervalDuration: Duration,
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

            val reconnectDitherDuration = HandlerUtils.reconnectDitherDuration(
                minReconnectDitherDuration,
                maxReconnectDitherDuration
            )

            val reconnectEventDuration = reconnectDitherDuration.minus(clientCloseGracePeriodDuration)

            ServerResponse
                .ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(
                    messages(*channels.toTypedArray())
                        .mergeWith(heartbeats())
                        .mergeWith(reconnections(reconnectEventDuration))
                        .take(
                            reconnectDitherDuration,
                            Schedulers.boundedElastic()
                        )
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

    /**
     * Reconnections
     *
     * @param reconnectEventDuration
     * @return
     */
    private fun reconnections(reconnectEventDuration: Duration): Flux<ServerSentEvent<String>> =
        Flux.interval(reconnectEventDuration, Schedulers.boundedElastic())
            .map {
                logger.info("[SSE] Pushing 'reconnect' message")
                ServerSentEvent.builder<String>()
                    .event("reconnect")
                    .data("{}")
                    .build()
            }
            .take(1L)

    /**
     * Heartbeats
     *
     * @return
     */
    private fun heartbeats(): Flux<ServerSentEvent<String>> {
        val heartbeatEventSupplier = {
            logger.info("[SSE] Pushing 'heartbeat' message")
            ServerSentEvent.builder<String>()
                .event("heartbeat")
                .data("{}")
                .build()
        }

        return Mono.fromCallable {
            heartbeatEventSupplier.invoke()
        }.concatWith(
            Flux.interval(heartbeatIntervalDuration, Schedulers.boundedElastic())
                .map {
                    heartbeatEventSupplier.invoke()
                }
        )
    }
}
