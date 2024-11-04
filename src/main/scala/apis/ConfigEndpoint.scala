package apis

import apis.model.{ConfigError, ConfigRequest, ConfigResponse}
import cats.Monad
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.effect.{Async, ExitCode, IO, IOApp}
import conf.{ServerConfig, TransferConfig, config}
import http.HttpServer
import io.circe.generic.auto.*
import srvc.TransferConfigService
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.cats.NettyCatsServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter

object ConfigEndpoint:
  private val configTransfer
    : PublicEndpoint[ConfigRequest, ConfigError, ConfigResponse, Any] =
    endpoint.post
      .in("config-transfer")
      .in(
        jsonBody[ConfigRequest]
          .example {
            ConfigRequest(
              5,
              5
            )
          }
      )
      .out(jsonBody[ConfigResponse])
      .errorOut(jsonBody[ConfigError])

  import cats.data.ValidatedNel
  import cats.instances.all.*
  import cats.syntax.all.*
  private def validateRequest[F[_]: Monad](
    request: ConfigRequest
  ): F[Either[ConfigError, TransferConfig]] =
    val tries: ValidatedNel[String, Int] =
      if (request.tries > 0) request.tries.validNel[String]
      else "tries should be more then 0".invalidNel
    val delay: ValidatedNel[String, Int] =
      if (request.delay > 0) request.tries.validNel[String]
      else "delay should be more then 0".invalidNel
    (tries, delay)
      .mapN((_, _) => TransferConfig.fromRequest(request))
      .toEither
      .left
      .map(nel => ConfigError(nel.toList.mkString(", ")))
      .pure[F]

  private def apiEndpoint[F[_]: Monad](
    service: TransferConfigService[F]
  ): ServerEndpoint[Any, F] =
    configTransfer.serverLogic { request =>
      for
        validate <- validateRequest(request)
        response <- validate match {
          case Right(config) =>
            service
              .set(config)
              .as(
                Right(
                  ConfigResponse(
                    s"config update, tries = ${request.tries}, delay = ${request.delay} seconds"
                  )
                )
              )
          case Left(error) => Left(error).pure[F]
        }
      yield response
    }

  def make[F[_]: Async](
    service: TransferConfigService[F]
  ): F[List[ServerEndpoint[Any, F]]] = apiEndpoint(service).pure[List].pure[F]

  def makeResource[F[_]: Async](
    service: TransferConfigService[F]
  ): Resource[F, List[ServerEndpoint[Any, F]]] =
    Resource.pure(apiEndpoint(service).pure[List])

object ConfigEndpointMain extends IOApp:

  def docs[F[_]](
    e: List[ServerEndpoint[Any, F]]
  ): List[ServerEndpoint[Any, F]] = SwaggerInterpreter()
    .fromServerEndpoints[F](e, "docs", "1.0.0")

  private def makeServer[F[_]: Async]: Resource[F, NettyCatsServer[F]] =
    Dispatcher
      .parallel[F]
      .map(NettyCatsServer[F](_))

  def bindServer[F[_]: Async](
    config: ServerConfig,
    endpoints: List[ServerEndpoint[Any, F]]
  ): Resource[F, NettyCatsServer[F]] =
    makeServer.map(
      _.host(config.host.value)
        .port(config.port.value)
        .addEndpoints(endpoints)
        .addEndpoints(docs(endpoints))
    )

  def run(args: List[String]): IO[ExitCode] =
    (for
      config      <- config[IO]
      _           <- IO.println(s"CONFIG $config")
      tcService   <- TransferConfigService.make[IO](config.tc)
      tcConfigOld <- tcService.get
      _           <- IO.println(s"TRANSER SERVICE GET: ${tcConfigOld}")
      endpoints   <- ConfigEndpoint.make[IO](tcService)
      _ <- HttpServer.makeResource[IO](config.http.server, endpoints)
        .use(_.serve() *> IO.never)
    yield ())
      .as(ExitCode.Success)

  import cats.implicits.*
  def a[F[_]: Async](e: List[ServerEndpoint[Any, F]]) = Dispatcher
    .parallel[F]
    .map(NettyCatsServer.apply[F](_))
    .use: server =>
      for bind <- server
          .port(8080)
          .host("localhost")
          .addEndpoints(e)
          .start()
//        _ <- IO.println(s"Go to http://localhost:${bind.port}/docs to open SwaggerUI. Press ENTER key to exit.")
//        _ <- IO.readLine
//        _ <- bind.stop()
      yield bind
