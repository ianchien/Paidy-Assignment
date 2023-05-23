package forex.services.rates.interpreters

import forex.services.rates.interpreters.OneFrameClient
import org.scalatest.flatspec.AnyFlatSpec
import forex.config.OneFrameClientConfig

import cats.effect.IO
import forex.domain.{ Currency, Price, Rate, Timestamp }

class OneFrameClientTest extends AnyFlatSpec {

  "genCurrencyPairs" should "gen pairs correctlly" in {
    val config = OneFrameClientConfig("localhost", 8888, "asd", 30, 3)
    val client = new OneFrameClient[IO](config)

    val expected = List("USDJPY", "JPYUSD")
    assert(expected === client.genCurrencyPairs(List("USD", "JPY")))

  }

  "jsonToRate" should "convert json to Rate correctlly" in {
    val config = OneFrameClientConfig("localhost", 8888, "asd", 30, 3)
    val client = new OneFrameClient[IO](config)

    val testJson = ujson.read("""{"from":"USD","to":"JPY", "price": 123, "time_stamp": "2020-01-01T12:34:56.123Z"}""")
    val expected =
      Rate(
        Rate.Pair(Currency.USD, Currency.JPY),
        Price(BigDecimal("123")),
        Timestamp.fromString("2020-01-01T12:34:56.123Z")
      )
    client.jsonToRate(testJson) match {
      case Right(result) => assert(expected === result)
      case Left(_)       =>
    }
  }

  "get" should "get the Rate from cache correctlly" in {
    val config = OneFrameClientConfig("localhost", 8888, "asd", 30, 3)
    val client = new OneFrameClient[IO](config)

    val expected = Rate(
      Rate.Pair(Currency.USD, Currency.JPY),
      Price(BigDecimal("123")),
      Timestamp.fromString("2020-01-01T12:34:56.123Z")
    )

    client.cache += ("USDJPY" -> expected)

    assert(expected === client.cache("USDJPY"))

  }
}
