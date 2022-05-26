package dev.goncalomartins.push.router

import dev.goncalomartins.push.handler.PublishHandler
import dev.goncalomartins.push.handler.SubscribeSSEHandler
import dev.goncalomartins.push.handler.SubscribeWSHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.router
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping

/**
 * Push router
 *
 * @property publishHandler
 * @property subscribeSSEHandler
 * @property subscribeWSHandler
 * @constructor Create empty Push router
 */
@Configuration
class PushRouter(
    private val publishHandler: PublishHandler,
    private val subscribeSSEHandler: SubscribeSSEHandler,
    private val subscribeWSHandler: SubscribeWSHandler
) {
    /**
     * Http
     *
     */
    @Bean
    fun http() = router {
        POST("/messages", publishHandler::publish)

        GET("/sse/messages", subscribeSSEHandler::subscribe)
    }

    /**
     * Ws
     *
     */
    @Bean
    fun ws() = SimpleUrlHandlerMapping(
        mapOf(
            "/ws/messages" to subscribeWSHandler
        ),
        -1
    )
}
