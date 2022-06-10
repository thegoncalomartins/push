package dev.goncalomartins.push.handler

import dev.goncalomartins.push.model.Message
import dev.goncalomartins.push.service.MessagingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono

/**
 * Publish Handler
 *
 * @property messagingService
 * @constructor Creates Publish Handler
 */
@Component
class PublishHandler(
    private val messagingService: MessagingService
) {

    /**
     * Logger
     */
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * Publish a message to a channel
     *
     * @param serverRequest represents the server-side HTTP request
     * @return a [Mono] that emits the [ServerResponse]
     */
    fun publish(
        serverRequest: ServerRequest
    ): Mono<ServerResponse> =
        serverRequest.bodyToMono<Message>()
            .flatMap { payload ->
                messagingService.publish(channel = payload.channel, message = payload.message)
                    .flatMap {
                        ServerResponse.accepted().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(mapOf("subscribers" to it))
                    }
                    .doOnSubscribe {
                        logger.info("[PUB] Publishing message to channel '{}'", payload.channel)
                    }
                    .doOnSuccess {
                        logger.info("[PUB] Successfully published message to channel '{}'", payload.channel)
                    }
                    .doOnError { error ->
                        logger.error("[PUB] Error while publishing message to channel '{}'", payload.channel, error)
                    }
            }
            .onErrorResume {
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).bodyValue(mapOf("error" to it.message))
            }
}
