package srvc

import apis.model.TransferErrorResponse
import cats.Monad
import cats.effect.*
import cats.effect.implicits.*
import cats.implicits.*
import cats.syntax.applicative.*
import data.domain.Transfer.Status
import data.domain.{Account, Transfer}
import repo.{AccountsRepo, TransfersRepo}
import srvc.model.TransferError

trait TransfersService[F[_]]:
  def transfer(transfer: Transfer): F[Either[TransferError, Transfer]]
  def checkTransferStatus(
    transactionReference: String
  ): F[Option[Transfer.Status]]

object TransfersService:
  def make[F[_]: Async: Monad](
    accountService: AccountService[F],
    transfersRepo: TransfersRepo[F],
    paymentGatewayService: PaymentGatewayService[F]
  ): F[TransfersService[F]] =
    Sync[F].delay:
      new TransfersService[F]:
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
                  _ <- checkTransferStatus(transfer).start
                yield Right(transfer)
              case Left(error) =>
                val updatedTransfer = transfer
                  .into[Transfer]
                  .withFieldConst(_.status, Status.FAILURE)
                  .transform
                for _ <- transfersRepo.insert(updatedTransfer)
                yield Left(TransferError(s"transfer failed: ${error.msg}"))
          yield result

        override def checkTransferStatus(
          transactionReference: String
        ): F[Option[Transfer.Status]] =
          for
            transfer <- transfersRepo.select(transactionReference)
            result   <- transfer.traverse: transfer =>
              transfer.status match
                case Transfer.Status.PENDING =>
                  checkTransferStatus(transfer)
                case status => status.pure[F]
          yield result

        private def handleTransferFailure(transfer: Transfer): F[Transfer.Status] =
          val updatedTransfer = transfer
            .into[Transfer]
            .withFieldConst(_.status, Status.FAILURE)
            .transform
          for
            _ <- transfersRepo.update(updatedTransfer)
            account <- accountService.lookup(updatedTransfer.accountId)
            _ <- account.traverse:
              accountService.enterWithdrawal(_, -transfer.amount)
          yield Transfer.Status.FAILURE

        private def handleTransferSuccess(transfer: Transfer): F[Transfer.Status] =
          val updatedTransfer = transfer
            .into[Transfer]
            .withFieldConst(_.status, Status.SUCCESS)
            .transform
          for _ <- transfersRepo.update(updatedTransfer)
            yield Transfer.Status.SUCCESS

        private def checkTransferStatus(
          transfer: Transfer
        ): F[Transfer.Status] =
          for
            status <- paymentGatewayService.checkTransferStatus(
              transfer.transactionReference
            )
            result <- status match
              case Right(transferStatus) =>
                Transfer.Status.valueOf(transferStatus.msg) match
                  case Transfer.Status.FAILURE =>
                    handleTransferFailure(transfer)
                  case Transfer.Status.SUCCESS =>
                    handleTransferSuccess(transfer)
                  case Transfer.Status.PENDING =>
                    Transfer.Status.PENDING.pure[F]
              case Left(errorResponse) =>
                Transfer.Status.FAILURE.pure[F]
          yield result

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

  def makeResource[F[_]: Async](
    accountService: AccountService[F],
    transferRepo: TransfersRepo[F],
    paymentGatewayService: PaymentGatewayService[F]
  ): Resource[F, TransfersService[F]] =
    Resource.eval:
      make(accountService, transferRepo, paymentGatewayService)
