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
import requests.Response
import ujson.Value

class OneFrameClient[F[_]: Applicative](config: OneFrameClientConfig) extends Algebra[F] {

  val targetUrl = "http://%s:%d/rates".format(config.host, config.port)
  val token     = config.token
  val timeout = config.timeout * 1000
  val updateFreq  = config.updateFreq * 60 * 1000
  val cache     = TrieMap[String, Rate]()

  override def get(pair: Rate.Pair): F[Error Either Rate] = {
    val key = "%s%s".format(pair.from.toString(), pair.to.toString())
    this.cache(key).asRight[Error].pure[F]
  }

  // Generate all possible currency pairs from supported currency list
  private def genCurrencyPairs(supportedCurrencies: Seq[String]): List[String] = {
    val pairs = new ListBuffer[String]()
    for {
      l <- supportedCurrencies
      r <- supportedCurrencies
      if l != r
    } pairs += "%s%s".format(l, r)
    pairs.toList
  }

  // Convert json object to Rate
  private def jsonToRate(jsonItem: Value): Rate = {
    val from: String      = jsonItem("from").str
    val to: String        = jsonItem("to").str
    val price: Double     = jsonItem("price").num
    val timestamp: String = jsonItem("time_stamp").str
    Rate(
      Rate.Pair(Currency.fromString(from), Currency.fromString(to)),
      Price(BigDecimal(price)),
      Timestamp.fromString(timestamp)
    )
  }

  // Get all currencies rate from One-Frame server
  def getAllRatesFromOneFrame: Response = {
    val currencyPairs = this.genCurrencyPairs(Currency.support)
    val params = {
      val listBuf = new ListBuffer[(String, String)]()
      for (currencyPair <- currencyPairs)
        listBuf += ("pair" -> currencyPair)
      listBuf.toList
    }

    val res: Response = requests.get(
      this.targetUrl,
      params = params,
      headers = Map("token" -> this.token),
      connectTimeout = config.timeout
    )
    res
  }

  // Update Rates to Cache every 3 minutes
  def autoUpdate() = {
    def updater() = {
      val res  = getAllRatesFromOneFrame
      val json = ujson.read(res.text())
      for (item <- json.arr) {
        val rate = jsonToRate(item)
        val key  = "%s%s".format(rate.pair.from.toString(), rate.pair.to.toString())
        cache += (key -> rate)
      }
    }
    val thread = new Thread {
      override def run: Unit =
        while (true) {
          updater()
          Thread.sleep(updateFreq.toLong) // 3 minutes
        }
    }
    thread.start

  }

}
