package dev.goncalomartins.push.service

import dev.goncalomartins.push.messaging.EventBus
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.ReactiveSubscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessagingServiceTest {

    private lateinit var messagingService: MessagingService

    private lateinit var eventBus: EventBus

    @BeforeAll
    fun setup() {
        eventBus = mock(EventBus::class.java)
        messagingService = MessagingService(eventBus)
    }

    @Test
    fun testPublish() {
        val subscribers = 0L

        whenever(eventBus.publish(anyString(), anyString()))
            .thenReturn(Mono.just(subscribers))

        val result = messagingService.publish("channel", "message")

        StepVerifier.create(result)
            .expectNextMatches { it == subscribers }
            .thenCancel()
            .verify()
    }

    @Test
    fun testSubscribe() {
        val channel = "channel"
        val message = "message"

        whenever(eventBus.subscribe(anyString()))
            .thenReturn(Flux.just(ReactiveSubscription.ChannelMessage(channel, message)))

        val result = messagingService.subscribe(channel)

        StepVerifier.create(result)
            .expectNextMatches { it.channel == channel && it.message == message }
            .thenCancel()
            .verify()
    }
}
