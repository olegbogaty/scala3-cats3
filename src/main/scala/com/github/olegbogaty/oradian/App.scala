package com.github.olegbogaty.oradian

import cats.effect.*
import cats.effect.instances.all.*
import cats.effect.std.Console
import com.github.olegbogaty.oradian.apis.{ConfigEndpoint, TransferEndpoint}
import com.github.olegbogaty.oradian.conf.Config
import com.github.olegbogaty.oradian.data.DbConnection
import com.github.olegbogaty.oradian.http.HttpServer
import com.github.olegbogaty.oradian.mock.PaymentGatewayService
import com.github.olegbogaty.oradian.mock.PaymentGatewayService.PaymentGatewayServiceStrategy
import com.github.olegbogaty.oradian.repo.{AccountsRepo, TransfersRepo}
import com.github.olegbogaty.oradian.srvc.{AccountService, TransferConfigService, TransferProcessingService}
import fs2.io.net.Network
import natchez.Trace
import natchez.Trace.Implicits.noop
import scribe.Scribe
import scribe.cats.given

object App extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    makeDependencies[IO].useForever.as(ExitCode.Success)

  // Other considerations:
  // You may directly construct service and class instances, bypassing the need for dependency injection.
  private def makeDependencies[F[
    _
  ]: Async: Temporal: Trace: Network: Console: Scribe]: Resource[F, Unit] =
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
