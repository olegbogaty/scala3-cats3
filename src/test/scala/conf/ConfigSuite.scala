package conf

import cats.effect.kernel.Resource
import cats.effect.{Async, IO}
import conf.Config.{AppConfig, DbConfig, HttpConfig, ServerConfig, TransferConfig}
import eu.timepit.refined.types.all.NonEmptyString
import eu.timepit.refined.types.net.UserPortNumber
import eu.timepit.refined.types.numeric.PosInt
import munit.CatsEffectSuite

import scala.concurrent.duration.*

class ConfigSuite extends CatsEffectSuite:

//  app = {
//    db = {
//      host = "localhost"
//      host = ${ ? DATABASE_HOST }
//      port = 5454
//      port = ${ ? DATABASE_PORT }
//      user = "oradian"
//      user = ${ ? DATABASE_USER }
//      pass = "oradian"
//      pass = ${ ? DATABASE_PASS }
//      name = "oradian"
//      name = ${ ? DATABASE_NAME }
//    }
//    #transfer config
//      tc = {
//      #e.g., 10
//      tries = 10
//      tries = ${ ? TRANSFER_CONFIG_TRIES }
//      #e.g., 10 seconds
//        delay = 10 seconds
//        delay = ${ ? TRANSFER_CONFIG_DELAY }
//    }
//    http {
//      server {
//        host = "localhost"
//        host = ${ ? SERVER_HOST }
//        port = 8080
//        port = ${ ? SERVER_PORT }
//      }
//    }
//  }

  private val testEnv: Map[String, String] = Map.newBuilder
    .addAll(
      List(
        "DATABASE_HOST"         -> "localhost",
        "DATABASE_HOST"         -> "5454",
        "DATABASE_USER"         -> "oradian",
        "DATABASE_PASS"         -> "oradian",
        "DATABASE_NAME"         -> "oradian",
        "TRANSFER_CONFIG_TRIES" -> "10",
        "TRANSFER_CONFIG_DELAY" -> "10",
        "SERVER_HOST"           -> "localhost",
        "SERVER_PORT"           -> "8080"
      )
    )
    .result()

  test("load and compare app config"):
    ConfigSuite
      .test[IO]
      .flatMap: conf =>
        IO(conf).assertEquals(ConfigSuite.appConfig)

object ConfigSuite:
  val dbConfig: DbConfig = DbConfig(
    host = NonEmptyString.unsafeFrom("localhost"),
    port = UserPortNumber.unsafeFrom(5454),
    user = NonEmptyString.unsafeFrom("oradian"),
    pass = NonEmptyString.unsafeFrom("oradian"),
    name = NonEmptyString.unsafeFrom("oradian")
  )

  val transferConfig: TransferConfig =
    TransferConfig(tries = PosInt.unsafeFrom(10), delay = 10.seconds)

  val serverConfig: ServerConfig = ServerConfig(
    host = NonEmptyString.unsafeFrom("localhost"),
    port = UserPortNumber.unsafeFrom(8080)
  )

  val httpConfig: HttpConfig = HttpConfig(server = serverConfig)

  val appConfig: AppConfig = AppConfig(
    db = dbConfig,
    tc = transferConfig,
    http = httpConfig
  )

  def testResource[F[_]: Async]: Resource[F, AppConfig] = Resource.eval:
    test

  def test[F[_]: Async]: F[AppConfig] = Config.make[F]
