package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    oneFrameClient: OneFrameClientConfig
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

case class OneFrameClientConfig(
    host: String,
    port: Int,
    token: String,
    timeout: Int, // connection timeout(seconds)
    updateFreq: Int // Update frequency(minutes)
)
