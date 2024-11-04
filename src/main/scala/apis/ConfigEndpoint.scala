package apis

import apis.model.{ConfigError, ConfigRequest, ConfigResponse}
import cats.effect.kernel.Resource
import cats.effect.{Async, ExitCode, IO, IOApp}
import cats.{Functor, Monad}
import conf.{TransferConfig, config}
import http.HttpServer
import io.circe.generic.auto.*
import srvc.TransferConfigService
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

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

  private def configTransferLogic[F[_]: Monad](
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

  // for test with swagger
  private val reviewSettings: PublicEndpoint[Unit, Unit, ConfigResponse, Any] =
    endpoint.get
      .in("config-transfer")
      .out(jsonBody[ConfigResponse])

  private def reviewSettingsLogic[F[_]: Functor](
    service: TransferConfigService[F]
  ): ServerEndpoint[Any, F] =
    reviewSettings.serverLogicSuccess: _ =>
      service.get.map: transferConfig =>
        ConfigResponse(
          s"config settings: tries = ${transferConfig.tries.value}, delay = ${transferConfig.delay}"
        )

  def make[F[_]: Async](
    service: TransferConfigService[F]
  ): F[List[ServerEndpoint[Any, F]]] =
    List(configTransferLogic(service), reviewSettingsLogic(service)).pure[F]

  def makeResource[F[_]: Async](
    service: TransferConfigService[F]
  ): Resource[F, List[ServerEndpoint[Any, F]]] =
    Resource.eval(make(service))

object ConfigEndpointMain extends IOApp: // TODO remove

  def run(args: List[String]): IO[ExitCode] =
    (for
      config      <- config[IO]
      _           <- IO.println(s"CONFIG $config")
      tcService   <- TransferConfigService.make[IO](config.tc)
      tcConfigOld <- tcService.get
      _           <- IO.println(s"TRANSER SERVICE GET: ${tcConfigOld}")
      endpoints   <- ConfigEndpoint.make[IO](tcService)
      _ <- HttpServer
        .makeResource[IO](config.http.server, endpoints)
        .use(_.serve() *> IO.never)
    yield ())
      .as(ExitCode.Success)
