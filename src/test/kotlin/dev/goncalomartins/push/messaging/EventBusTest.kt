package dev.goncalomartins.push.messaging

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventBusTest {

    private lateinit var eventBus: EventBus

    private lateinit var stringRedisTemplate: ReactiveStringRedisTemplate

    @BeforeAll
    fun setup() {
        stringRedisTemplate = mock(ReactiveStringRedisTemplate::class.java)
        eventBus = EventBus(stringRedisTemplate)
    }

    @Test
    fun testPublish() {
        val subscribers = 0L

        whenever(stringRedisTemplate.convertAndSend(anyString(), anyString()))
            .thenReturn(Mono.just(subscribers))

        val result = eventBus.publish("channel", "message")

        StepVerifier.create(result)
            .expectNextMatches { it == subscribers }
            .thenCancel()
            .verify()
    }

    @Test
    fun testSubscribe() {
        val channel = "channel"
        val message = "message"

        whenever(stringRedisTemplate.listenToChannel(anyString()))
            .thenReturn(Flux.just(ReactiveSubscription.ChannelMessage(channel, message)))

        val result = eventBus.subscribe(channel)

        StepVerifier.create(result)
            .expectNextMatches { it.channel == channel && it.message == message }
            .thenCancel()
            .verify()
    }
}
