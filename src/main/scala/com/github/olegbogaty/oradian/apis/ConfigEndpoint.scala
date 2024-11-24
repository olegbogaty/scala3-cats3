package com.github.olegbogaty.oradian.apis

import cats.effect.Async
import cats.effect.kernel.Resource
import cats.{Functor, Monad}
import com.github.olegbogaty.oradian.apis.model.{ConfigErrorResponse, ConfigRequest, ConfigResponse}
import com.github.olegbogaty.oradian.conf.Config
import com.github.olegbogaty.oradian.conf.Config.TransferConfig
import com.github.olegbogaty.oradian.srvc.TransferConfigService
import io.circe.generic.auto.*
import scribe.Scribe
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

object ConfigEndpoint:
  //  /config-transfer
  //  An endpoint to manage the processing configuration of transfer requests,
  //  allowing adjustments of parameters like status check frequency and retry limits.
  private val configTransfer
    : PublicEndpoint[ConfigRequest, ConfigErrorResponse, ConfigResponse, Any] =
    endpoint.post
      .in("config-transfer")
      .in(
        jsonBody[ConfigRequest]
          .example:
            ConfigRequest.example
      )
      .out(jsonBody[ConfigResponse])
      .errorOut(jsonBody[ConfigErrorResponse])

  import cats.data.ValidatedNel
  import cats.syntax.all.*
  // for test with swagger
  private val reviewSettings: PublicEndpoint[Unit, Unit, ConfigResponse, Any] =
    endpoint.get
      .in("config-transfer")
      .out(jsonBody[ConfigResponse])

  def makeResource[F[_]: Async: Scribe](
    service: TransferConfigService[F]
  ): Resource[F, List[ServerEndpoint[Any, F]]] =
    Resource.eval(make(service))

  def make[F[_]: Async: Scribe](
    service: TransferConfigService[F]
  ): F[List[ServerEndpoint[Any, F]]] =
    List(configTransferLogic(service), reviewSettingsLogic(service)).pure[F]

  private def configTransferLogic[F[_]: Monad: Scribe](
    service: TransferConfigService[F]
  ): ServerEndpoint[Any, F] =
    configTransfer.serverLogic: request =>
      for
        _        <- Scribe[F].debug(s"received request: $request")
        validate <- validateRequest(request)
        response <- validate match
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
      yield response

  private def validateRequest[F[_]: Monad](
    request: ConfigRequest
  ): F[Either[ConfigErrorResponse, TransferConfig]] =
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
      .map(nel => ConfigErrorResponse(nel.toList.mkString(", ")))
      .pure[F]

  private def reviewSettingsLogic[F[_]: Functor](
    service: TransferConfigService[F]
  ): ServerEndpoint[Any, F] =
    reviewSettings.serverLogicSuccess: _ =>
      service.get.map: transferConfig =>
        ConfigResponse(
          s"config settings: tries = ${transferConfig.tries.value}, delay = ${transferConfig.delay}"
        )
