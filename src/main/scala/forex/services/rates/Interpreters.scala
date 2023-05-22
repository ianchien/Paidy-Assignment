package forex.services.rates

import cats.Applicative
import interpreters._
import forex.config.OneFrameClientConfig

object Interpreters {
  def startup[F[_]: Applicative](config: OneFrameClientConfig): Algebra[F] = {
    val oneFrameClient = new OneFrameClient[F](config)
    oneFrameClient.autoUpdate()
    oneFrameClient
  }

}
