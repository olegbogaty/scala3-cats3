import apis.{ConfigEndpoint, TransferEndpoint}
import cats.effect.*
import cats.effect.std.Console
import conf.Config
import data.DbConnection
import fs2.io.net.Network
import http.HttpServer
import natchez.Trace
import natchez.Trace.Implicits.noop
import repo.{AccountsRepo, TransfersRepo}
import srvc.{AccountService, PaymentGatewayService, TransferConfigService, TransferService}

object App extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    makeDependencies[IO].useForever.as(ExitCode.Success)

  private def makeDependencies[F[_]: Async: Temporal: Trace: Network: Console]
    : Resource[F, Unit] =
    for
      config                <- Config.makeResource
      session               <- DbConnection.single(config.db)
      accountRepo           <- AccountsRepo.makeResource(session)
      transferRepo          <- TransfersRepo.makeResource(session)
      paymentGatewayService <- PaymentGatewayService.makeResource
      transferConfigService <- TransferConfigService.makeResource(
        config.tc
      )
      accountService <- AccountService.makeResource(accountRepo)
      transfersService <- TransferService.makeResource(
        accountService,
        transferRepo,
        paymentGatewayService,
        transferConfigService
      )
      transferEndpoints <- TransferEndpoint.makeResource(transfersService)
      configEndpoints   <- ConfigEndpoint.makeResource(transferConfigService)
      _ <- HttpServer.makeResource(
        config.http.server,
        transferEndpoints ++ configEndpoints
      )
    yield ()
