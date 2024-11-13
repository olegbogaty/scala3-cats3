package srvc

import apis.model.TransferErrorResponse
import cats.Monad
import cats.effect.*
import cats.effect.implicits.*
import cats.implicits.*
import cats.syntax.applicative.*
import data.domain.Transfer.Status
import data.domain.{Account, Transfer}
import logs.Log
import repo.TransfersRepo
import srvc.model.TransferError

import scala.concurrent.duration.*

trait TransferService[F[_]] extends Log[F]:
  def transfer(transfer: Transfer): F[Either[TransferError, Transfer]]
  def checkTransferStatus( // TODO refactor and move into transfer check service?
    transactionReference: String
  ): F[Option[Transfer.Status]]

object TransferService:
  def makeResource[F[_]: Async](
    accountService: AccountService[F],
    transferRepo: TransfersRepo[F],
    paymentGatewayService: PaymentGatewayService[F],
    transferConfigService: TransferConfigService[F]
  ): Resource[F, TransferService[F]] =
    Resource.eval:
      make(
        accountService,
        transferRepo,
        paymentGatewayService,
        transferConfigService
      )

  def make[F[_]: Async: Monad](
    accountService: AccountService[F],
    transfersRepo: TransfersRepo[F],
    paymentGatewayService: PaymentGatewayService[F],
    transferConfigService: TransferConfigService[F]
  ): F[TransferService[F]] =
    Sync[F].delay:
      new TransferService[F]:
        private def checkTransfer(
          account: Account,
          transfer: Transfer
        ): Either[String, (Account, Transfer)] =
          val amount = Either.cond(
            account.balance >= transfer.amount,
            (account, transfer),
            "insufficient amount"
          )
          val sameAccount = Either.cond(
            transfer.accountId != transfer.recipientAccount,
            (account, transfer),
            "transfer from and to same account is not allowed"
          )
          amount.flatMap(_ => sameAccount.map(identity))

        import io.scalaland.chimney.dsl.*
        private def enterTransfer(
          account: Account,
          transfer: Transfer
        ): F[Either[TransferError, Transfer]] =
          for
            response <- paymentGatewayService.enterTransfer(transfer)
            result <- response match
              case Right(response) =>
                for
                  _ <- accountService.enterWithdrawal(account, transfer.amount)
                  _ <- transfersRepo.insert(transfer)
                  config <- transferConfigService.get
                  _ <- checkTransferStatus(
                    transfer,
                    config.tries.value,
                    config.delay
                  ).start
                yield Right(transfer)
              case Left(error) =>
                val updatedTransfer = transfer
                  .into[Transfer]
                  .withFieldConst(_.status, Status.FAILURE)
                  .transform
                for
                  _ <- log.info(s"transfer rejected: ${error.msg}")
                  _ <- transfersRepo.insert(updatedTransfer)
                yield Left(TransferError(s"transfer failed: ${error.msg}"))
          yield result

        override def checkTransferStatus(
          transactionReference: String
        ): F[Option[Transfer.Status]] =
          for
            transfer <- transfersRepo.select(transactionReference)
            result <- transfer.traverse: transfer =>
              transfer.status.pure[F]
          yield result

        private def handleTransferFailure(transfer: Transfer): F[Unit] =
          val updatedTransfer = transfer
            .into[Transfer]
            .withFieldConst(_.status, Status.FAILURE)
            .transform
          for
            _       <- transfersRepo.update(updatedTransfer)
            account <- accountService.lookup(updatedTransfer.accountId)
            _ <- account.traverse:
              accountService.enterWithdrawal(_, -transfer.amount)
          yield ()

        private def handleTransferSuccess(transfer: Transfer): F[Unit] =
          val updatedTransfer = transfer
            .into[Transfer]
            .withFieldConst(_.status, Status.SUCCESS)
            .transform
          for _ <- transfersRepo.update(updatedTransfer)
          yield ()

        private def checkTransferStatus(
          transfer: Transfer,
          tries: Int,
          delay: FiniteDuration
        ): F[Unit] = {
          for
            _ <- log.info(s"checking transfer status, $tries tries left")
            status <- paymentGatewayService.checkTransferStatus(
              transfer.transactionReference
            )
            _ <- log.info(s"transfer status: ${status.msg}")
            result <- Transfer.Status.valueOf(status.msg) match
              case Transfer.Status.FAILURE =>
                handleTransferFailure(transfer)
              case Transfer.Status.SUCCESS =>
                handleTransferSuccess(transfer)
              case Transfer.Status.PENDING =>
                if (tries <= 0)
                  log.info("0 tries left, rollback the transfer") *>
                    handleTransferFailure(transfer)
                else
                  Temporal[F].sleep(delay) *>
                    checkTransferStatus(transfer, tries - 1, delay)
          yield ()
        }

        def transfer(transfer: Transfer): F[Either[TransferError, Transfer]] =
          for
            option <- accountService.lookup(transfer.accountId)
            result <- option match
              case Some(account) =>
                checkTransfer(account, transfer) match
                  case Right((account, transfer)) =>
                    enterTransfer(account, transfer)
                  case Left(error) =>
                    Left(TransferError(error)).pure[F]
              case None => Left(TransferError("account not found")).pure[F]
          yield result
