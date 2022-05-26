package dev.goncalomartins.push.handler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.goncalomartins.push.service.MessagingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

/**
 * Subscribe w s controller
 *
 * @constructor Create empty Subscribe w s controller
 */
@Component
class SubscribeWSHandler(
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
            session.send(
                messages(*channels)
                    .map(session::textMessage)
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
    private fun channels(session: WebSocketSession) =
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
    private fun messages(vararg channels: String) = messagingService.subscribe(*channels)
        .map { message ->
            logger.info("[WS] Pushing message from channel '{}'", message.channel)
            objectMapper.writeValueAsString(message)
        }
}
