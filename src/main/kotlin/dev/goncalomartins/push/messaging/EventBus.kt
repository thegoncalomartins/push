package dev.goncalomartins.push.messaging

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Event bus
 *
 * @constructor Create empty Event bus
 */
@Component
class EventBus(
    private val stringRedisTemplate: ReactiveStringRedisTemplate
) {

    /**
     * Object mapper
     */
    private val objectMapper = jacksonObjectMapper()

    /**
     * Publish
     *
     * @param channel
     * @param message
     * @return
     */
    fun publish(channel: String, message: Any): Mono<Long> =
        stringRedisTemplate.convertAndSend(
            channel,
            if (message is String) message else objectMapper.writeValueAsString(message)
        )

    /**
     * Subscribe
     *
     * @param channels
     * @return
     */
    fun subscribe(vararg channels: String): Flux<out ReactiveSubscription.Message<String, String>> =
        stringRedisTemplate.listenToChannel(*channels)
}
