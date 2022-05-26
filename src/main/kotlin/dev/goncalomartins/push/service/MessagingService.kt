package dev.goncalomartins.push.service

import dev.goncalomartins.push.messaging.EventBus
import dev.goncalomartins.push.model.Message
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Messaging service
 *
 * @property eventBus
 * @constructor Create empty Messaging service
 */
@Service
class MessagingService(
    private val eventBus: EventBus
) {
    /**
     * Publish
     *
     * @param channel
     * @param message
     * @return
     */
    fun publish(channel: String, message: Any): Mono<Long> = eventBus.publish(channel, message)

    /**
     * Subscribe
     *
     * @param channels
     * @return
     */
    fun subscribe(vararg channels: String): Flux<Message> = eventBus.subscribe(*channels).map {
        Message(it.channel, it.message)
    }
}
