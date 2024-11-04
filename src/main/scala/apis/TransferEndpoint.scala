package apis

import apis.model.TransferRequest.transferRequestExample
import apis.model.{TransferError, TransferRequest, TransferResponse}
import cats.effect.{ExitCode, IO, IOApp}
import io.circe.generic.auto.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.cats.NettyCatsServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.*

import scala.util.Random

// /enter-transfer
// An endpoint to accept transfer requests
// Each request must include essential information such as :
//  ∗ sender account
//  ∗ recipient bank code(numeric)
//  ∗ recipient account (numeric)
//  ∗ amount
//  ∗ transaction reference

object EnterTransferEndpoint:
  val enterTransfer: PublicEndpoint[TransferRequest, TransferError, TransferResponse, Any] =
    endpoint.post
      .in("enter-transfer")
      .in(jsonBody[TransferRequest].example(transferRequestExample))
      .out(jsonBody[TransferResponse])
      .errorOut(jsonBody[TransferError])

  def apiEndpoint =
    enterTransfer.serverLogic(request =>
      IO {
        Either.cond(
          Random.nextBoolean,
          TransferResponse("ok"),
          TransferError("insufficient balance")
        )
      }
    )

  val docEndpoint: List[ServerEndpoint[Any, IO]] = SwaggerInterpreter()
    .fromServerEndpoints[IO](List(apiEndpoint), "docs", "1.0.0")

object EnterTransferEndpointMain extends IOApp:
  import EnterTransferEndpoint.*
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
