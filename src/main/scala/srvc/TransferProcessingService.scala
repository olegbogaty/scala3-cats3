package srvc

import apis.model.{TransferErrorResponse, TransferResponse}
import cats.effect.*
import cats.effect.implicits.*
import cats.instances.all.*
import cats.syntax.all.*
import data.domain.{Account, Transfer}
import mock.PaymentGatewayService
import repo.TransfersRepo
import srvc.model.TransferError

import scala.concurrent.duration.*

trait TransferProcessingService[F[_]]:
  def enterTransfer(request: Transfer): F[Either[TransferError, Transfer]]
  def checkTransferStatus(transferRef: String): F[Option[Transfer.Status]]

object TransferProcessingService:
  def makeResource[F[_]: Concurrent: Temporal](
    gatewayService: PaymentGatewayService[F],
    accountService: AccountService[F],
    transfersRepo: TransfersRepo[F],
    configService: TransferConfigService[F]
  ): Resource[F, TransferProcessingService[F]] =
    Resource.eval:
      make(gatewayService, accountService, transfersRepo, configService)

  def make[F[_]: Concurrent: Temporal](
    gatewayService: PaymentGatewayService[F],
    accountService: AccountService[F],
    transfersRepo: TransfersRepo[F],
    configService: TransferConfigService[F]
  ): F[TransferProcessingService[F]] =
    for
      activeHandlers <- Ref.of[F, Map[String, Fiber[F, Throwable, Unit]]](
        Map.empty
      )
      service <- make(
        gatewayService,
        accountService,
        transfersRepo,
        configService,
        activeHandlers
      )
    yield service

  private def make[F[_]: Concurrent: Temporal](
    gatewayService: PaymentGatewayService[F],
    accountService: AccountService[F],
    transfersRepo: TransfersRepo[F],
    configService: TransferConfigService[F],
    activeHandlers: Ref[F, Map[String, Fiber[F, Throwable, Unit]]]
  ): F[TransferProcessingService[F]] =
    new TransferProcessingService[F]:
      override def enterTransfer(
        transfer: Transfer
      ): F[Either[TransferError, Transfer]] =
        for
          option <- accountService.lookup(transfer.accountId)
          result <- option match
            case Some(account) =>
              validateTransfer(account, transfer) match
                case Right((account, transfer)) =>
                  handleGatewayResponse(account, transfer)
                case Left(error) => Left(error).pure[F]
            case None => Left(TransferError("Account not found")).pure[F]
        yield result

      override def checkTransferStatus(
        transactionReference: String
      ): F[Option[Transfer.Status]] =
        for
          transfer <- transfersRepo.select(transactionReference)
          result <- transfer.traverse: transfer =>
            transfer.status.pure[F]
        yield result

      private def validateTransfer(
        account: Account,
        transfer: Transfer
      ): Either[TransferError, (Account, Transfer)] =
        val amount = Either.cond(
          account.balance >= transfer.amount,
          (account, transfer),
          TransferError("insufficient amount")
        )
        val sameAccount = Either.cond(
          transfer.accountId != transfer.recipientAccount,
          (account, transfer),
          TransferError("transfer from and to same account is not allowed")
        )
        amount.flatMap(_ => sameAccount.map(identity))

      private def handleGatewayResponse(account: Account, transfer: Transfer) =
        for
          gatewayResponse <- gatewayService.enterTransfer(transfer)
          result <- gatewayResponse match
            case Right(_) =>
              startTransferHandler(account, transfer) *>
                // transfer accepted and pending
                Right(transfer).pure[F]
            case Left(error) =>
              // transfer rejected
              Left(TransferError(s"transfer rejected: ${error.msg}"))
                .pure[F]
        yield result

      private def startTransferHandler(
        account: Account,
        transfer: Transfer
      ): F[Unit] =
        for
          config <- configService.get
          fiber <- handleTransferStatus(
            account,
            transfer,
            config.tries.value,
            config.delay
          ).start
          _ <- activeHandlers.update(
            _ + (transfer.transactionReference -> fiber)
          )
          // temporary lock money in transfers repo until get response from gateway service
          _ <- transfersRepo.insert(transfer) *> accountService.enterWithdrawal(
            account,
            transfer.amount
          )
        yield ()

      private def handleTransferStatus(
        account: Account,
        transfer: Transfer,
        tries: Int,
        delay: FiniteDuration
      ): F[Unit] =
        if (tries <= 0)
          // 0 attempts left, rollback transfer
          finalizeTransfer(account, transfer, success = false)
        else
          Temporal[F].sleep(delay) *>
            gatewayService
              .checkTransferStatus(transfer.transactionReference)
              .flatMap:
                case TransferResponse("SUCCESS") =>
                  finalizeTransfer(account, transfer, success = true)
                case TransferResponse("FAILURE") =>
                  finalizeTransfer(account, transfer, success = false)
                case TransferResponse("PENDING") =>
                  handleTransferStatus(account, transfer, tries - 1, delay)

      private def finalizeTransfer(
        account: Account,
        transfer: Transfer,
        success: Boolean
      ): F[Unit] =
        for
          // cancel async transfer and remove from state
          _ <- activeHandlers.update: map =>
            map.get(transfer.transactionReference).foreach(_.cancel)
            map - transfer.transactionReference
          _ <-
            if (!success)
              // rollback transfer in case of failure
              transfersRepo.update(
                transfer.copy(status = Transfer.Status.FAILURE)
              ) *>
                accountService.enterWithdrawal(account, -transfer.amount)
            else
              transfersRepo.update(
                transfer.copy(status = Transfer.Status.SUCCESS)
              )
        yield ()
    .pure[F]
