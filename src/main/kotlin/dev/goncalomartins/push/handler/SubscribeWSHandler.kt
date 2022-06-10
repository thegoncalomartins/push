package dev.goncalomartins.push.handler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.goncalomartins.push.service.MessagingService
import dev.goncalomartins.push.utils.HandlerUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

/**
 * Subscribe w s Handler
 *
 * @constructor Create empty Subscribe w s controller
 */
@Component
class SubscribeWSHandler(
    @Value("\${push.reconnect.dither.min.duration}")
    private val minReconnectDitherDuration: Duration,
    @Value("\${push.reconnect.dither.max.duration}")
    private val maxReconnectDitherDuration: Duration,
    @Value("\${push.client.close.grace.period.duration}")
    private val clientCloseGracePeriodDuration: Duration,
    @Value("\${push.heartbeat.interval.duration}")
    private val heartbeatIntervalDuration: Duration,
    private val messagingService: MessagingService
) : WebSocketHandler {

    /**
     * Object mapper
     */
    private val objectMapper = jacksonObjectMapper()

    /**
     * Logger
     */
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handle
     *
     * @param session
     * @return
     */
    override fun handle(session: WebSocketSession): Mono<Void> = Mono.fromCallable { channels(session) }
        .flatMap { channels ->
            val reconnectDitherDuration = HandlerUtils.reconnectDitherDuration(
                minReconnectDitherDuration,
                maxReconnectDitherDuration
            )

            val reconnectEventDuration = reconnectDitherDuration.minus(clientCloseGracePeriodDuration)

            val pongSupplier = {
                logger.info("[WS] Pushing 'pong' message")
                session.send(
                    Mono.just(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "event" to "pong",
                                "data" to emptyMap<String, Any?>()
                            )
                        )
                    ).map(session::textMessage)
                )
            }

            session.send(
                messages(*channels)
                    .map(session::textMessage)
            ).and(
                session.send(
                    pings()
                        .map(session::textMessage)
                )
            ).and(
                session.send(
                    reconnections(reconnectEventDuration)
                        .map(session::textMessage)
                )
            ).and(
                session
                    .receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .map { payload: String ->
                        objectMapper.readerFor(Map::class.java).readValue<Map<String, Any?>>(payload)
                    }.flatMap { payload ->
                        when (payload["event"]) {
                            "ping" -> {
                                logger.info("[WS] Receiving 'ping' message")
                                pongSupplier.invoke()
                            }
                            "pong" -> {
                                logger.info("[WS] Receiving 'pong' message")
                                Mono.empty()
                            }
                            else -> Mono.empty()
                        }
                    }
            ).take(
                reconnectDitherDuration,
                Schedulers.boundedElastic()
            )
        }
        .doOnSubscribe {
            logger.info("[WS] Connection has been established")
        }.doFinally {
            logger.info("[WS] Connection has been terminated")
        }.onErrorResume(IllegalArgumentException::class.java) {
            session.send(
                Mono.just(objectMapper.writeValueAsString(mapOf("error" to it.message))).map(session::textMessage)
            )
        }
        .onErrorResume {
            logger.error("[WS] Connection had an error: '{}'", it.message, it)
            session.send(
                Mono.just(objectMapper.writeValueAsString(mapOf("error" to it.message))).map(session::textMessage)
            )
        }

    /**
     * Channels
     *
     * @param session
     */
    private fun channels(session: WebSocketSession): Array<String> =
        UriComponentsBuilder
            .fromUri(session.handshakeInfo.uri)
            .build()
            .queryParams["channels"]
            ?.first()
            ?.split(",")
            ?.toTypedArray()
            ?: throw IllegalArgumentException("'channels' query parameter required")

    /**
     * Messages
     *
     * @param channels
     */
    private fun messages(vararg channels: String): Flux<String> = messagingService.subscribe(*channels)
        .map { message ->
            logger.info("[WS] Pushing message from channel '{}'", message.channel)
            objectMapper.writeValueAsString(
                mapOf(
                    "event" to "message",
                    "data" to mapOf(
                        "channel" to message.channel,
                        "message" to message.message
                    )
                )
            )
        }

    /**
     * Reconnections
     *
     * @param reconnectEventDuration
     * @return
     */
    private fun reconnections(reconnectEventDuration: Duration): Flux<String> =
        Flux.interval(reconnectEventDuration, Schedulers.boundedElastic())
            .map {
                logger.info("[WS] Pushing 'reconnect' message")
                objectMapper.writeValueAsString(
                    mapOf(
                        "event" to "reconnect",
                        "data" to emptyMap<String, Any?>()
                    )
                )
            }
            .take(1L)

    /**
     * Pings
     *
     * @return
     */
    private fun pings(): Flux<String> {
        val pingEventSupplier = {
            logger.info("[WS] Pushing 'ping' message")
            objectMapper.writeValueAsString(
                mapOf(
                    "event" to "ping",
                    "data" to emptyMap<String, Any?>()
                )
            )
        }

        return Mono.fromCallable {
            pingEventSupplier.invoke()
        }.concatWith(
            Flux.interval(heartbeatIntervalDuration, Schedulers.boundedElastic())
                .map {
                    pingEventSupplier.invoke()
                }
        )
    }
}
