import apis.{ConfigEndpoint, TransferEndpoint}
import cats.effect.*
import cats.effect.std.Console
import conf.Config
import data.DbConnection
import fs2.io.net.Network
import http.HttpServer
import mock.PaymentGatewayService
import mock.PaymentGatewayService.PaymentGatewayServiceStrategy
import natchez.Trace
import natchez.Trace.Implicits.noop
import repo.{AccountsRepo, TransfersRepo}
import srvc.{AccountService, TransferConfigService, TransferProcessingService}

object App extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    makeDependencies[IO].useForever.as(ExitCode.Success)

  // Other considerations:
  // You may directly construct service and class instances, bypassing the need for dependency injection.
  private def makeDependencies[F[_]: Async: Temporal: Trace: Network: Console]
    : Resource[F, Unit] =
    for
      config       <- Config.makeResource
      session      <- DbConnection.single(config.db)
      accountRepo  <- AccountsRepo.makeResource(session)
      transferRepo <- TransfersRepo.makeResource(session)
      paymentGatewayService <- PaymentGatewayService.makeResource(
        PaymentGatewayServiceStrategy.PendingThenSuccess
      )
      transferConfigService <- TransferConfigService.makeResource(
        config.tc
      )
      accountService <- AccountService.makeResource(accountRepo)
      transfersService <- TransferProcessingService.makeResource(
        paymentGatewayService,
        accountService,
        transferRepo,
        transferConfigService
      )
      transferEndpoints <- TransferEndpoint.makeResource(transfersService)
      configEndpoints   <- ConfigEndpoint.makeResource(transferConfigService)
      _ <- HttpServer.makeResource(
        config.http.server,
        transferEndpoints ++ configEndpoints
      )
    yield ()
