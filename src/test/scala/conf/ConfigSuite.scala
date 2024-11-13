package conf

import cats.effect.{Async, IO}
import cats.effect.kernel.{Resource, Sync}
import conf.Config.AppConfig
import munit.CatsEffectSuite

//import scala.jdk.CollectionConverters.*

class ConfigSuite extends CatsEffectSuite, EnvHacker:

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
//      base = "oradian"
//      base = ${ ? DATABASE_BASE }
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

  private val testEnv: Map[String, String] = Map.newBuilder.addAll(List(
    "DATABASE_HOST"         -> "localhost",
    "DATABASE_HOST"         -> "5454",
    "DATABASE_USER"         -> "oradian",
    "DATABASE_PASS"         -> "oradian",
    "DATABASE_BASE"         -> "oradian",
    "TRANSFER_CONFIG_TRIES" -> "10",
    "TRANSFER_CONFIG_DELAY" -> "10",
    "SERVER_HOST"           -> "localhost",
    "SERVER_PORT"           -> "8080"
  )).result()

  test("load config") {
    import ConfigSuite.*
    // setEnv(testEnv.asJava)
    make[IO].flatMap(IO.println)
  }

object ConfigSuite:
  def make[F[_]: Async]: F[AppConfig] = Config.make[F]

  def makeResource[F[_] : Async]: Resource[F, AppConfig] = Resource.eval:
    make
