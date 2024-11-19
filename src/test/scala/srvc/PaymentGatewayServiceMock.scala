package srvc

import apis.model.{TransferErrorResponse, TransferResponse}
import cats.effect.kernel.{Resource, Sync}
import cats.syntax.all.*
import data.domain.Transfer

object PaymentGatewayServiceMock:

  trait PaymentGatewayServiceStrategy
  object PaymentGatewayServiceStrategy:
    case object RejectTransfer        extends PaymentGatewayServiceStrategy
    case object AlwaysPendingTransfer extends PaymentGatewayServiceStrategy
    case object SuccessTransfer       extends PaymentGatewayServiceStrategy
    case object FailureTransfer       extends PaymentGatewayServiceStrategy
    case object PendingThenSuccess    extends PaymentGatewayServiceStrategy
  def test[F[_]: Sync](
    strategy: PaymentGatewayServiceStrategy
  ): F[PaymentGatewayService[F]] =
    Sync[F].delay:
      new PaymentGatewayService[F]:
        override def enterTransfer(
          transfer: Transfer
        ): F[Either[TransferErrorResponse, TransferResponse]] =
          strategy match
            case PaymentGatewayServiceStrategy.RejectTransfer =>
              Left(TransferErrorResponse("transfer rejected")).pure[F]
            case _ =>
              Right(TransferResponse("transfer status: PENDING")).pure[F]

        override def checkTransferStatus(
          transactionReference: String
        ): F[TransferResponse] =
          strategy match
            case PaymentGatewayServiceStrategy.SuccessTransfer =>
              TransferResponse(Transfer.Status.SUCCESS.toString).pure[F]
            case PaymentGatewayServiceStrategy.AlwaysPendingTransfer =>
              TransferResponse(Transfer.Status.PENDING.toString).pure[F]
            case PaymentGatewayServiceStrategy.FailureTransfer |
                PaymentGatewayServiceStrategy.RejectTransfer =>
              TransferResponse(Transfer.Status.FAILURE.toString).pure[F]
            case PaymentGatewayServiceStrategy.PendingThenSuccess => ??? // TODO
  def testResource[F[_]: Sync](
    strategy: PaymentGatewayServiceStrategy
  ): Resource[F, PaymentGatewayService[F]] =
    Resource.eval:
      test(strategy)
