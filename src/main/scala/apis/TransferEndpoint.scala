package apis

import apis.model.TransferRequest.transferRequestExample
import apis.model.{TransferErrorResponse, TransferRequest, TransferResponse, TransferStatusRequest}
import cats.{Functor, Monad}
import cats.effect.kernel.Resource
import cats.effect.{Async, ExitCode, IO, IOApp}
import data.domain.Transfer
import http.HttpServer
import io.circe.generic.auto.*
import srvc.TransferService
import srvc.model.TransferError
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

// /enter-transfer
// An endpoint to accept transfer requests
object TransferEndpoint:
  private val enterTransfer: PublicEndpoint[
    TransferRequest,
    TransferErrorResponse,
    TransferResponse,
    Any
  ] =
    endpoint.post
      .in("enter-transfer")
      .in(jsonBody[TransferRequest].example(transferRequestExample))
      .out(jsonBody[TransferResponse])
      .errorOut(jsonBody[TransferErrorResponse])

  import cats.data.ValidatedNel
  import cats.syntax.all.*
  private def validateRequest[F[_]: Monad](
    request: TransferRequest
  ): F[Either[TransferErrorResponse, Transfer]] =
    val tries: ValidatedNel[String, BigDecimal] =
      if (request.amount > 0) request.amount.validNel[String]
      else "amount should be more then 0".invalidNel
    val delay: ValidatedNel[String, Int] =
      if (request.senderAccount != request.recipientAccount)
        request.senderAccount.validNel[String]
      else
        "sender account number should not be a recipient account number".invalidNel
    (tries, delay)
      .mapN((_, _) => Transfer.fromRequest(request))
      .toEither
      .left
      .map(nel => TransferErrorResponse(nel.toList.mkString(", ")))
      .pure[F]

  private def enterTransferLogic[F[_]: Monad](
    service: TransferService[F]
  ): ServerEndpoint[Any, F] =
    enterTransfer.serverLogic: request =>
      for
        validate <- validateRequest(request)
        response <- validate match
          case Right(transfer) =>
            service
              .transfer(transfer)
              .map:
                _.map: right =>
                  TransferResponse(s"transfer status: ${right.status}")
                .left
                  .map: left =>
                    TransferErrorResponse(s"transfer error: ${left.msg}")
          case Left(error) => Left(error).pure[F]
      yield response

  private val checkTransferStatus
    : Endpoint[Unit, TransferStatusRequest, Unit, TransferResponse, Any] =
    endpoint.post
      .in("check-transfer-status")
      .in(
        jsonBody[TransferStatusRequest]
          .example(TransferStatusRequest("unique transaction reference"))
      )
      .out(jsonBody[TransferResponse])

  private def checkTransferStatusLogic[F[_] : Functor](
    service: TransferService[F]
  ): ServerEndpoint[Any, F] =
    checkTransferStatus.serverLogicSuccess: request =>
      service.checkTransferStatus(request.transactionReference).map: transferStatus =>
        TransferResponse(
          s"transfer status: $transferStatus"
        )

  def make[F[_]: Async](
    service: TransferService[F]
  ): F[List[ServerEndpoint[Any, F]]] =
    List(enterTransferLogic(service), checkTransferStatusLogic(service))
      .pure[F]

  def makeResource[F[_]: Async](
    service: TransferService[F]
  ): Resource[F, List[ServerEndpoint[Any, F]]] =
    Resource.eval(make(service))

object TransferEndpointMain extends IOApp:
  private val mockService = new TransferService[IO]:
    override def transfer(
      transfer: Transfer
    ): IO[Either[TransferError, Transfer]] = IO(Left(TransferError("error")))
    override def checkTransferStatus(transactionReference: String): IO[Option[Transfer.Status]] =
      IO(Some(Transfer.Status.PENDING))
  def run(args: List[String]): IO[ExitCode] =
    (for
      config    <- conf.config[IO]
      endpoints <- TransferEndpoint.make[IO](mockService)
      _ <- HttpServer
        .makeResource[IO](config.http.server, endpoints)
        .use(_.serve() *> IO.never)
    yield ())
      .as(ExitCode.Success)
