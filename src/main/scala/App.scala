import apis.{ConfigEndpoint, TransferEndpoint}
import cats.effect.*
import cats.effect.std.Console
import conf.config
import data.DbConnection
import fs2.io.net.Network
import http.HttpServer
import natchez.Trace
import natchez.Trace.Implicits.noop
import repo.{AccountsRepo, TransfersRepo}
import srvc.{AccountService, PaymentGatewayService, TransferConfigService, TransferService}

object App extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    makeDependencies[IO].useForever
      .as(ExitCode.Success)

  private def makeDependencies[F[_]: Async: Temporal: Trace: Network: Console] =
    for
      config                <- Resource.eval(config)
      session               <- DbConnection.single(config.db)
      accountRepo           <- AccountsRepo.makeResource(session)
      transferRepo          <- TransfersRepo.makeResource(session)
      transferConfig        <- Resource.eval(Ref[F].of(config.tc))
      paymentGatewayService <- PaymentGatewayService.makeResource
      transferConfigService <- TransferConfigService.makeResource(
        transferConfig
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
      server <- HttpServer.makeResource(
        config.http.server,
        transferEndpoints ++ configEndpoints
      )
    yield server
