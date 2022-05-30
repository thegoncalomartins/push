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
import org.springframework.http.codec.ServerSentEvent
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import java.net.URI

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SubscribeSSEHandlerTest {

    @Autowired
    private lateinit var subscribeWebTestClient: WebTestClient

    @Autowired
    private lateinit var stringRedisTemplate: ReactiveStringRedisTemplate

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun subscribeWithChannels() {
        val messageFoo = "message to channel 'foo1'"
        val channelFoo = "foo1"

        val messageBar = "message to channel 'bar1'"
        val channelBar = "bar1"

        val block = { channel: String, message: String -> stringRedisTemplate.convertAndSend(channel, message) }

        val blocks = block.invoke(channelFoo, messageFoo)
            .concatWith(
                block.invoke(channelBar, messageBar)
            )

        val publishing = blocks
            .repeat()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe()

        val messages =
            subscribeWebTestClient
                .get()
                .uri(URI.create("/sse/messages?channels=$channelFoo,$channelBar"))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk
                .returnResult<ServerSentEvent<String>>()
                .responseBody

        StepVerifier.create(messages, 2)
            .expectNextMatches { sse ->
                sse.event() == "message" &&
                    (
                        sse.data() == objectMapper.writeValueAsString(
                            mapOf(
                                "channel" to channelBar,
                                "message" to messageBar
                            )
                        ) || sse.data() == objectMapper.writeValueAsString(
                            mapOf(
                                "channel" to channelFoo,
                                "message" to messageFoo
                            )
                        )
                        )
            }
            .expectNextMatches { sse ->
                sse.event() == "message" &&
                    (
                        sse.data() == objectMapper.writeValueAsString(
                            mapOf(
                                "channel" to channelBar,
                                "message" to messageBar
                            )
                        ) || sse.data() == objectMapper.writeValueAsString(
                            mapOf(
                                "channel" to channelFoo,
                                "message" to messageFoo
                            )
                        )
                        )
            }
            .then {
                while (!publishing.isDisposed) {
                    publishing.dispose()
                }
            }
            .thenCancel()
            .verify()
    }

    @Test
    fun subscribeWithoutChannels() {
        subscribeWebTestClient
            .get()
            .uri(URI.create("/sse/messages"))
            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("error").isEqualTo("'channels' query parameter required")
    }
}
