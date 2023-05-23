package forex.services.rates.interpreters

import forex.services.rates.Algebra
import forex.domain.Currency
import cats.Applicative
import cats.syntax.applicative._
import cats.syntax.either._
import forex.domain.{ Price, Rate, Timestamp }
import forex.services.rates.errors._
import forex.config.OneFrameClientConfig
import scala.collection.mutable.ListBuffer
import scala.collection.concurrent.TrieMap

// import cats.effect.IO
import requests._
import ujson.Value
import requests.{ RequestFailedException, RequestsException }

class OneFrameClient[F[_]: Applicative](config: OneFrameClientConfig) extends Algebra[F] {

  val targetUrl           = "http://%s:%d/rates".format(config.host, config.port)
  val token               = config.token
  val timeout             = config.timeout * 1000
  val updateFreq          = config.updateFreq * 60 * 1000
  val cache               = TrieMap[String, Rate]()
  val supportedCurrencies = Currency.support

  override def get(pair: Rate.Pair): F[Error Either Rate] = {
    val key = "%s%s".format(pair.from.toString(), pair.to.toString())
    this.cache(key).asRight[Error].pure[F]
  }

  // Generate all possible currency pairs from supported currency list
  private[interpreters] def genCurrencyPairs(supportedCurrencies: Seq[String]): List[String] = {
    val pairs = new ListBuffer[String]()
    for {
      l <- supportedCurrencies
      r <- supportedCurrencies
      if l != r
    } pairs += "%s%s".format(l, r)
    pairs.toList
  }

  // Convert json object to Rate
  private[interpreters] def jsonToRate(jsonItem: Value): Either[Exception, Rate] =
    try {
      val from: String      = jsonItem("from").str
      val to: String        = jsonItem("to").str
      val price: Double     = jsonItem("price").num
      val timestamp: String = jsonItem("time_stamp").str
      Right(
        Rate(
          Rate.Pair(Currency.fromString(from), Currency.fromString(to)),
          Price(BigDecimal(price)),
          Timestamp.fromString(timestamp)
        )
      )
    } catch {
      case e: Exception => Left(e)
    }

  // Get all currencies rate from One-Frame server
  def getAllRatesFromOneFrame(currencies: Seq[String]): Either[RequestsException, Response] = {
    val currencyPairs = this.genCurrencyPairs(currencies)
    val params = {
      val listBuf = new ListBuffer[(String, String)]()
      for (currencyPair <- currencyPairs)
        listBuf += ("pair" -> currencyPair)
      listBuf.toList
    }

    try {
      val res: Response = requests.get(
        this.targetUrl,
        params = params,
        headers = Map("token" -> this.token),
        connectTimeout = config.timeout
      )
      if (!res.is2xx) {
        Left(new RequestFailedException(res))
      } else {
        Right(res)
      }
    } catch {
      case e: RequestsException =>
        Left(e)
    }
  }

  // Update Rates to Cache every 3 minutes
  def autoUpdate() = {
    def updater =
      getAllRatesFromOneFrame(supportedCurrencies) match {
        case Left(e) => Error.OneFrameInternalFailed(e.message)
        case Right(r) =>
          val json = ujson.read(r)
          for (item <- json.arr)
            jsonToRate(item) match {
              case Left(e) => Error.OneFrameInternalFailed(e.toString())
              case Right(rate) =>
                val key = "%s%s".format(rate.pair.from.toString(), rate.pair.to.toString())
                cache += (key -> rate)
            }
      }

    val thread = new Thread {
      override def run: Unit =
        while (true) {
          updater
          Thread.sleep(updateFreq.toLong) // 3 minutes
        }
    }
    thread.start

  }

}
