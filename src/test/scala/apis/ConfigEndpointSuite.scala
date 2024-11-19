package apis

import apis.model.{ConfigRequest, ConfigResponse}
import cats.effect.*
import conf.Config
import conf.Config.TransferConfig
import io.circe.generic.auto.*
import munit.CatsEffectSuite
import srvc.TransferConfigService
import sttp.client3.*
import sttp.client3.circe.*
import sttp.model.StatusCode

class ConfigEndpointSuite extends CatsEffectSuite:

  private val configRequest = ConfigRequest(tries = 5, delay = 5)
  private val initialConfig = TransferConfig.fromRequest(configRequest)

  def withServer[T](service: TransferConfigService[IO])(
    test: SttpBackend[Identity, Any] => IO[T]
  ): IO[Unit] =
    val serverResource =
      for
        config <- Config.makeResource[IO]
        routes <- ConfigEndpoint.makeResource(service)
        server <- http.HttpServer.makeResource(config.http.server, routes)
      yield server
    serverResource.use { server =>
      for
        binding <- server.serve()
        _       <- test(HttpURLConnectionBackend())
        _       <- IO.blocking(binding).guarantee(binding.stop())
      yield ()
    }

  test("GET /config-transfer should return the current configuration"):
    for
      service <- TransferConfigService.make[IO](initialConfig)
      _ <- withServer(service) { backend =>
        val res = basicRequest
          .get(uri"http://localhost:8080/config-transfer")
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
    for
      service <- TransferConfigService.make[IO](initialConfig)
      _ <- withServer(service) { backend =>
        val updateResponse = basicRequest
          .post(uri"http://localhost:8080/config-transfer")
          .body(newConfig)
          .send(backend)

        val fetchResponse = basicRequest
          .get(uri"http://localhost:8080/config-transfer")
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
