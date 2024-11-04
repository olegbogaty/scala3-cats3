import apis.{ConfigEndpoint, TransferEndpoint}
import cats.Applicative
import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import conf.config
import data.DbConnection
import data.domain.Transfer
import fs2.io.net.Network
import http.HttpServer
import natchez.Trace
import natchez.Trace.Implicits.noop
import repo.{AccountsRepo, TransfersRepo}
import srvc.{TransferConfigService, TransferService}

object App extends IOApp:
  private def mockService[F[_]: Applicative] =
    new TransferService[F]: // TODO remove
      override def transfer(transfer: Transfer): F[Unit] = ().pure[F]

  private def makeDependencies[F[_]: Async: Temporal: Trace: Network: Console] =
    for
      config         <- Resource.eval(config)
      session        <- DbConnection.single(config.db)
      accountRepo    <- AccountsRepo.makeResource(session)
      transferRepo   <- TransfersRepo.makeResource(session)
      transferConfig <- Resource.eval(Ref[F].of(config.tc))
      transferConfigService <- TransferConfigService.makeResource(
        transferConfig
      )
      transferEndpoint <- TransferEndpoint.makeResource(mockService)
      configEndpoints  <- ConfigEndpoint.makeResource(transferConfigService)
      server <- HttpServer.makeResource(
        config.http.server,
        transferEndpoint ++ configEndpoints
      )
    yield server

  override def run(args: List[String]): IO[ExitCode] =
    makeDependencies[IO]
      .use: server =>
        for
          _ <- server.serve()
          _ <- IO.never
        yield ()
      .as(ExitCode.Success)
