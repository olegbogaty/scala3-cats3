package apis

import apis.model.{ConfigRequest, ConfigResponse}
import cats.effect.*
import cats.effect.kernel.Resource
import cats.effect.std.Console
import conf.Config
import conf.Config.TransferConfig
import eu.timepit.refined.types.net.UserPortNumber
import http.HttpServer
import io.circe.generic.auto.*
import munit.CatsEffectSuite
import munit.catseffect.IOFixture
import srvc.TransferConfigService
import sttp.client3.*
import sttp.client3.circe.*
import sttp.model.StatusCode

class ConfigEndpointSuite extends CatsEffectSuite:

  val httpServer: IOFixture[HttpServer[IO]] = ResourceSuiteLocalFixture(
    "http-server",
    makeServer[IO]
  )
  private val httpServerPort = UserPortNumber.unsafeFrom(
    8888
  ) // CANNOT USE FIXTURE FOR SUITE, NEED TO CHANGE
  private val configRequest = ConfigRequest(tries = 5, delay = 5)
  private val initialConfig = TransferConfig.fromRequest(configRequest)

  def makeServer[F[_]: Async: Console]: Resource[F, HttpServer[F]] =
    for
      config  <- Config.makeResource
      service <- TransferConfigService.makeResource(initialConfig)
      routes  <- ConfigEndpoint.makeResource(service)
      server <- http.HttpServer.makeResource(
        config.http.server.copy(port = httpServerPort),
        routes
      )
    yield server

  override def munitFixtures: Seq[IOFixture[HttpServer[IO]]] = List(httpServer)

  def withServer[T](
    test: SttpBackend[Identity, Any] => IO[T]
  ): IO[Unit] =
    test(HttpURLConnectionBackend()).void

  test("GET /config-transfer should return the current configuration"):
    for _ <- withServer { backend =>
        val res = basicRequest
          .get(uri"http://localhost:$httpServerPort/config-transfer")
          .response(asJson[ConfigResponse])
          .send(backend)
        IO {
          assertEquals(res.code, StatusCode.Ok)
          assertEquals(
            res.body,
            Right(
              ConfigResponse("config settings: tries = 5, delay = 5 seconds")
            )
          )
        }
      }
    yield ()

  test("POST /config-transfer should update the configuration"):
    val newConfig = ConfigRequest(tries = 10, delay = 5)
    for _ <- withServer { backend =>
        val updateResponse = basicRequest
          .post(uri"http://localhost:$httpServerPort/config-transfer")
          .body(newConfig)
          .send(backend)

        val fetchResponse = basicRequest
          .get(uri"http://localhost:$httpServerPort/config-transfer")
          .response(asJson[ConfigResponse])
          .send(backend)

        for
          _ <- IO(assertEquals(updateResponse.code, StatusCode.Ok))
          _ <- IO {
            assertEquals(fetchResponse.code, StatusCode.Ok)
            assertEquals(
              fetchResponse.body,
              Right(
                ConfigResponse("config settings: tries = 10, delay = 5 seconds")
              )
            )
          }
        yield ()
      }
    yield ()
