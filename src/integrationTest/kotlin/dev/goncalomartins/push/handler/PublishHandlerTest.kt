package dev.goncalomartins.push.handler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.scheduler.Schedulers
import java.net.URI

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PublishHandlerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var stringRedisTemplate: ReactiveStringRedisTemplate

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun testPublishWithSubscribers() {
        val channel = "channel1"
        val message = "message1"

        val body = objectMapper.writeValueAsString(mapOf("channel" to channel, "message" to message))

        val subscription = stringRedisTemplate.listenToChannel(channel)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe()

        Thread.sleep(500)

        webTestClient
            .post()
            .uri(URI.create("/messages"))
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("subscribers").isEqualTo(1)

        while (!subscription.isDisposed) {
            subscription.dispose()
        }
    }

    @Test
    fun testPublishWithoutSubscribers() {
        val channel = "channel2"
        val message = "message2"

        val body = objectMapper.writeValueAsString(mapOf("channel" to channel, "message" to message))

        webTestClient
            .post()
            .uri(URI.create("/messages"))
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("subscribers").isEqualTo(0)
    }
}
