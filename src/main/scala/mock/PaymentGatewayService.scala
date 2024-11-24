package mock

import apis.model.{TransferErrorResponse, TransferResponse}
import cats.effect.{Ref, Resource, Sync}
import cats.syntax.all.*
import data.domain.Transfer

// a mock service that encapsulates interaction with an external payment gateway
trait PaymentGatewayService[F[_]]:
  // a method returning either success (transfer accepted and pending) or failure (transfer rejected).
  def enterTransfer(
    transfer: Transfer
  ): F[Either[TransferErrorResponse, TransferResponse]]
  // a method returning pending, success, or failure, based on the unique transfer identifier.
  def checkTransferStatus(transactionReference: String): F[TransferResponse]

object PaymentGatewayService:
  def makeResource[F[_]: Sync](
    strategy: PaymentGatewayServiceStrategy
  ): Resource[F, PaymentGatewayService[F]] =
    Resource.eval:
      make(strategy)

  def make[F[_]: Sync](
    strategy: PaymentGatewayServiceStrategy
  ): F[PaymentGatewayService[F]] =
    for
      state   <- Ref.of(Map.empty[String, Transfer.Status])
      service <- make(strategy, state)
    yield service

  private def make[F[_]: Sync](
    strategy: PaymentGatewayServiceStrategy,
    state: Ref[F, Map[String, Transfer.Status]]
  ) =
    scribe.cats[F].info(s"Strategy for payment gateway: $strategy") *>
      Sync[F].delay:
        new PaymentGatewayService[F]:
          override def enterTransfer(
            transfer: Transfer
          ): F[Either[TransferErrorResponse, TransferResponse]] =
            strategy match
              case PaymentGatewayServiceStrategy.RejectTransfer =>
                Left(TransferErrorResponse("transfer rejected")).pure[F]
              case _ =>
                state.update(
                  _ + (transfer.transactionReference -> Transfer.Status.PENDING)
                ) *>
                  Right(TransferResponse("transfer status: PENDING")).pure[F]

          override def checkTransferStatus(
            transactionReference: String
          ): F[TransferResponse] =
            strategy match
              case PaymentGatewayServiceStrategy.SuccessTransfer =>
                TransferResponse(Transfer.Status.SUCCESS.toString)
                  .pure[F] <* state.update(_ - transactionReference)
              case PaymentGatewayServiceStrategy.AlwaysPendingTransfer =>
                TransferResponse(Transfer.Status.PENDING.toString)
                  .pure[F] <* state.update(_ - transactionReference)
              case PaymentGatewayServiceStrategy.FailureTransfer |
                  PaymentGatewayServiceStrategy.RejectTransfer =>
                TransferResponse(Transfer.Status.FAILURE.toString)
                  .pure[F] <* state.update(_ - transactionReference)
              case PaymentGatewayServiceStrategy.PendingThenSuccess =>
                state.get
                  .map:
                    _.get(transactionReference)
                      .map: _ =>
                        TransferResponse(Transfer.Status.SUCCESS.toString)
                      .getOrElse:
                        // not a case
                        TransferResponse(Transfer.Status.FAILURE.toString)
                // format: off
                <* state.update(_ - transactionReference)
                // format: on

  trait PaymentGatewayServiceStrategy

  object PaymentGatewayServiceStrategy:
    case object RejectTransfer extends PaymentGatewayServiceStrategy

    case object AlwaysPendingTransfer extends PaymentGatewayServiceStrategy

    case object SuccessTransfer extends PaymentGatewayServiceStrategy

    case object FailureTransfer extends PaymentGatewayServiceStrategy

    case object PendingThenSuccess extends PaymentGatewayServiceStrategy
