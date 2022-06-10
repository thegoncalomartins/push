package dev.goncalomartins.push.utils

import java.time.Duration
import java.util.Random

class HandlerUtils {
    companion object {
        /**
         * Reconnect dither duration
         *
         * @param minReconnectDitherDuration the minimum reconnection dither duration
         * @param maxReconnectDitherDuration the maximum reconnection dither duration
         * @return each client's max connection lifetime. Helps in spreading subsequent client reconnects across time
         */
        fun reconnectDitherDuration(
            minReconnectDitherDuration: Duration,
            maxReconnectDitherDuration: Duration
        ): Duration = Duration.ofSeconds(
            minReconnectDitherDuration.toSeconds()
                .plus(
                    Random()
                        .nextLong(
                            maxReconnectDitherDuration.minus(minReconnectDitherDuration).toSeconds()
                        )
                )
        )
    }
}
