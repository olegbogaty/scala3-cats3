package repo

import cats.effect.{Resource, Sync}
import cats.implicits.*
import cats.instances.all.*
import data.domain.Transfer
import repo.TransfersRepo

import java.time.LocalDateTime
import scala.collection.concurrent.TrieMap

class TransfersRepoSuite {}

object TransfersRepoSuite:
  val testTransfer: Transfer = Transfer(
    0, // accountId
    BigDecimal(500),
    Transfer.Status.PENDING,
    2, // recipientAccount
    3, // recipientBankCode
    "transactionReference",
    LocalDateTime.now
  )
  def test[F[_]: Sync]: F[TransfersRepo[F]] =
    Sync[F].delay:
      new TransfersRepo[F]:
        private val transfers = TrieMap[String, Transfer](
          testTransfer.transactionReference -> testTransfer
        )

        def insert(transfer: Transfer): F[Unit] =
          Sync[F]
            .delay:
              transfers.put(transfer.transactionReference, transfer)
            .void

        def select(transactionReference: String): F[Option[Transfer]] =
          Sync[F].delay:
            transfers.get(transactionReference)

        def update(transfer: Transfer): F[Unit] =
          Sync[F].delay:
            transfers.get(transfer.transactionReference) match
              case Some(_) =>
                transfers.update(transfer.transactionReference, transfer)
              case None => ()

        def delete(transfer: Transfer): F[Unit] =
          Sync[F]
            .delay:
              transfers.remove(transfer.transactionReference)
            .void

  def testResource[F[_]: Sync]: Resource[F, TransfersRepo[F]] =
    Resource.eval(test)
