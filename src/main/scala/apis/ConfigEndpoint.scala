package apis

import apis.model.{ConfigError, ConfigRequest, ConfigResponse, TransferError, TransferRequest, TransferResponse}
import cats.effect.{ExitCode, IO, IOApp}
import io.circe.Encoder
import io.circe.generic.auto.*
import io.circe.Encoder.encodeDuration
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.cats.NettyCatsServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.*
import scala.jdk.DurationConverters.*

import scala.util.Random
object ConfigEndpoint:

  val configTransfer
  : PublicEndpoint[ConfigRequest, ConfigError, ConfigResponse, Any] =
    endpoint.post
      .in("config-transfer")
      .in(jsonBody[ConfigRequest].example {
        ConfigRequest(
          5, 5
        )
      })
      .out(jsonBody[ConfigResponse])
      .errorOut(jsonBody[ConfigError])

  val apiEndpoint =
    configTransfer.serverLogic(request => IO {
      Either.cond(Random.nextBoolean, ConfigResponse("ok"), ConfigError("invalid parameters"))
    })

  val docEndpoint: List[ServerEndpoint[Any, IO]] = SwaggerInterpreter()
    .fromServerEndpoints[IO](List(apiEndpoint), "docs", "1.0.0")

object TransferConfigEndpointMain extends IOApp:
  import ConfigEndpoint.*
  def run(args: List[String]): IO[ExitCode] =
    NettyCatsServer
      .io()
      .use: server =>
        for
          bind <- server
            .port(8080)
            .host("localhost")
            .addEndpoint(apiEndpoint)
            .addEndpoints(docEndpoint)
            .start()
          _ <- IO.println(
            s"Go to http://localhost:${bind.port}/docs to open SwaggerUI. Press ENTER key to exit."
          )
          _ <- IO.readLine
          _ <- bind.stop()
        yield bind
      .as(ExitCode.Success)
