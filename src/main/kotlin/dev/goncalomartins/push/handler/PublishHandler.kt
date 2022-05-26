package dev.goncalomartins.push.handler

import dev.goncalomartins.push.messaging.EventBus
import dev.goncalomartins.push.model.Message
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono

/**
 * Publish controller
 *
 * @property eventBus
 * @constructor Create empty Publish controller
 */
@Component
class PublishHandler(
    private val eventBus: EventBus
) {

    /**
     * Logger
     */
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * Publish message
     *
     * @param serverRequest
     * @return
     */
    fun publish(
        serverRequest: ServerRequest
    ): Mono<ServerResponse> =
        serverRequest.bodyToMono<Message>()
            .flatMap { payload ->
                logger.info("Publishing message to channel '{}'", payload.channel)

                eventBus.publish(channel = payload.channel, message = payload.message)
                    .flatMap {
                        ServerResponse.accepted().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(mapOf("subscribers" to it))
                    }
                    .doOnSuccess {
                        logger.info("Successfully published message to channel '{}'", payload.channel)
                    }
                    .doOnError { error ->
                        logger.error("Error while publishing message to channel '{}'", payload.channel, error)
                    }
            }.onErrorResume(DecodingException::class.java) {
                ServerResponse.badRequest().bodyValue(mapOf("error" to "Invalid request body"))
            }
            .onErrorResume {
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).bodyValue(mapOf("error" to it.message))
            }
}
