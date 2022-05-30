package dev.goncalomartins.push.handler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import org.springframework.web.reactive.socket.client.WebSocketClient
import reactor.core.scheduler.Schedulers
import java.net.URI
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubscribeWSHandlerTest {

    @Autowired
    private lateinit var stringRedisTemplate: ReactiveStringRedisTemplate

    @LocalServerPort
    private var port: Int = 0

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun subscribeWithChannels() {
        val messageFoo = "message to channel 'foo2'"
        val channelFoo = "foo2"

        val messageBar = "message to channel 'bar2'"
        val channelBar = "bar2"

        val block = { channel: String, message: String -> stringRedisTemplate.convertAndSend(channel, message) }

        val blocks = block.invoke(channelFoo, messageFoo)
            .concatWith(
                block.invoke(channelBar, messageBar)
            )

        val publishing = blocks
            .repeat()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe()

        val messages = mutableListOf<String>()

        val client: WebSocketClient = ReactorNettyWebSocketClient()

        client.execute(URI.create("ws://localhost:$port/ws/messages?channels=$channelFoo,$channelBar")) { session ->
            session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(messages::add)
                .take(2)
                .then()
        }.block(Duration.ofSeconds(5L))

        while (!publishing.isDisposed) {
            publishing.dispose()
        }

        assertAll(
            { assertEquals(2, messages.size) },
            {
                assertTrue(
                    messages.containsAll(
                        listOf(
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "channel" to channelFoo,
                                    "message" to messageFoo
                                )
                            ),
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "channel" to channelBar,
                                    "message" to messageBar
                                )
                            )
                        )
                    )
                )
            }
        )
    }

    @Test
    fun subscribeWithoutChannels() {
        val messages = mutableListOf<String>()

        val client: WebSocketClient = ReactorNettyWebSocketClient()

        client.execute(URI.create("ws://localhost:$port/ws/messages")) { session ->
            session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(messages::add)
                .take(1)
                .then()
        }.block(Duration.ofSeconds(1L))

        assertAll(
            { assertEquals(1, messages.size) },
            {
                assertEquals(
                    messages[0],
                    objectMapper.writeValueAsString(
                        mapOf(
                            "error" to "'channels' query parameter required"
                        )
                    )
                )
            }
        )
    }
}
