package srvc

import apis.model.{TransferErrorResponse, TransferResponse}
import cats.effect.{Resource, Sync}
import cats.implicits.*
import data.domain.Transfer

import scala.util.Random

trait PaymentGatewayService[F[_]]:
// a method returning either success (transfer accepted and pending) or failure (transfer rejected).
  def enterTransfer(transfer: Transfer): F[Either[TransferErrorResponse, TransferResponse]]
// a method returning pending, success, or failure, based on the unique transfer identifier.
  def checkTransferStatus(transfer: Transfer): F[Either[TransferErrorResponse, TransferResponse]]

object PaymentGatewayService:
  def make[F[_]: Sync]: F[PaymentGatewayService[F]] =
    Sync[F].delay:
      new PaymentGatewayService[F]:
        def enterTransfer(transfer: Transfer): F[Either[TransferErrorResponse, TransferResponse]] =
          Either.cond(Random.nextBoolean, TransferResponse("transfer accepted and pending"), TransferErrorResponse("transfer rejected")).pure[F]
        def checkTransferStatus(transfer: Transfer): F[Either[TransferErrorResponse, TransferResponse]] =
          val statuses = Transfer.Status.values.map(_.toString)
          Either.cond(Random.nextBoolean, TransferResponse(statuses(Random.nextInt(statuses.length))), TransferErrorResponse("transfer error")).pure[F]
          
  def makeResource[F[_]: Sync]: Resource[F, PaymentGatewayService[F]] =
    Resource.eval(make)