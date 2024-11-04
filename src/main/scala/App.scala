import apis.ConfigEndpoint
import cats.effect.*
import cats.effect.std.Console
import conf.config
import data.DBConnection
import fs2.io.net.Network
import http.HttpServer
import natchez.Trace
import natchez.Trace.Implicits.noop
import repo.{AccountsRepo, TransfersRepo}
import srvc.TransferConfigService

object App extends IOApp:
  def program[F[_]: Async: Temporal: Trace: Network: Console] =
    for
      config         <- Resource.eval(config)
      session        <- DBConnection.single(config.db)
      accountRepo    <- AccountsRepo.makeResource(session)
      transferRepo   <- TransfersRepo.makeResource(session)
      transferConfig <- Resource.eval(Ref[F].of(config.tc))
      transferConfigService <- TransferConfigService.makeResource(
        transferConfig
      )
      configEndpoints <- ConfigEndpoint.makeResource(transferConfigService)
      server <- HttpServer.makeResource(config.http.server, configEndpoints)
    yield server

  override def run(args: List[String]): IO[ExitCode] =
    program[IO]
      .use: server =>
        for
          _ <- server.serve()
          _ <- IO.never
        yield ()
      .as(ExitCode.Success)
