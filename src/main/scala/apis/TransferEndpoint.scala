package apis

import apis.model.TransferRequest.transferRequestExample
import apis.model.{TransferError, TransferRequest, TransferResponse}
import cats.effect.kernel.Resource
import cats.effect.{Async, ExitCode, IO, IOApp}
import cats.implicits.*
import data.domain.Transfer
import http.HttpServer
import io.circe.generic.auto.*
import srvc.TransferService
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

import scala.util.Random

// /enter-transfer
// An endpoint to accept transfer requests
object TransferEndpoint:
  private val enterTransfer
    : PublicEndpoint[TransferRequest, TransferError, TransferResponse, Any] =
    endpoint.post
      .in("enter-transfer")
      .in(jsonBody[TransferRequest].example(transferRequestExample))
      .out(jsonBody[TransferResponse])
      .errorOut(jsonBody[TransferError])

  private def enterTransferLogic[F[_]: Async](
    service: TransferService[F]
  ): ServerEndpoint[Any, F] =
    enterTransfer.serverLogic(request =>
      Either
        .cond(
          Random.nextBoolean,
          TransferResponse("transfer success"),
          TransferError("insufficient balance")
        )
        .pure[F]
    )

  def make[F[_]: Async](
    service: TransferService[F]
  ): F[List[ServerEndpoint[Any, F]]] =
    List(enterTransferLogic(service) /*, reviewSettingsLogic(service)*/ )
      .pure[F]

  def makeResource[F[_]: Async](
    service: TransferService[F]
  ): Resource[F, List[ServerEndpoint[Any, F]]] =
    Resource.eval(make(service))

object TransferEndpointMain extends IOApp:
  private val mockService = new TransferService[IO]:
    override def transfer(transfer: Transfer): IO[Unit] = IO.unit
  def run(args: List[String]): IO[ExitCode] =
    (for
      config    <- conf.config[IO]
      endpoints <- TransferEndpoint.make[IO](mockService)
      _ <- HttpServer
        .makeResource[IO](config.http.server, endpoints)
        .use(_.serve() *> IO.never)
    yield ())
      .as(ExitCode.Success)
