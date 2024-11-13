package srvc

import apis.model.{TransferErrorResponse, TransferResponse}
import cats.effect.{Resource, Sync}
import cats.implicits.*
import data.domain.Transfer

import scala.util.Random

// a mock service that encapsulates interaction with an external payment gateway
trait PaymentGatewayService[F[_]]:
// a method returning either success (transfer accepted and pending) or failure (transfer rejected).
  def enterTransfer(transfer: Transfer): F[Either[TransferErrorResponse, TransferResponse]]
// a method returning pending, success, or failure, based on the unique transfer identifier.
  def checkTransferStatus(transactionReference: String): F[TransferResponse]

object PaymentGatewayService:
  def make[F[_]: Sync]: F[PaymentGatewayService[F]] =
    Sync[F].delay:
      new PaymentGatewayService[F]:
        def enterTransfer(transfer: Transfer): F[Either[TransferErrorResponse, TransferResponse]] =
          Either.cond(Random.nextBoolean, TransferResponse("transfer accepted and pending"), TransferErrorResponse("transfer rejected")).pure[F]
        def checkTransferStatus(transactionReference: String): F[TransferResponse] =
          val statuses = Transfer.Status.values.map(_.toString)
          TransferResponse(statuses(Random.nextInt(statuses.length))).pure[F]
          
  def makeResource[F[_]: Sync]: Resource[F, PaymentGatewayService[F]] =
    Resource.eval(make)