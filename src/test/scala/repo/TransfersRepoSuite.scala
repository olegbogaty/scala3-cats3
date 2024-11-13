package repo

import cats.effect.{IO, Resource, Sync}
import cats.implicits.*
import data.domain.Transfer
import munit.CatsEffectSuite
import repo.TransfersRepoSuite.testTransfer

import java.time.LocalDateTime
import scala.collection.concurrent.TrieMap

class TransfersRepoSuite extends CatsEffectSuite:

  private val initialTransfer = testTransfer

  def withRepo(testCode: TransfersRepo[IO] => IO[Unit]): IO[Unit] =
    TransfersRepoSuite.test[IO].flatMap(testCode)

  test("insert should store a new transfer"):
    withRepo: repo =>
      val newTransfer = Transfer(
        accountId = 1,
        amount = BigDecimal(1000),
        status = Transfer.Status.PENDING,
        recipientAccount = 3,
        recipientBankCode = 4,
        transactionReference = "newTransaction",
        transferDate = LocalDateTime.now
      )
      for
        _         <- repo.insert(newTransfer)
        retrieved <- repo.select(newTransfer.transactionReference)
      yield assertEquals(retrieved, Some(newTransfer))

  test("select should retrieve an existing transfer"):
    withRepo: repo =>
      for retrieved <- repo.select(initialTransfer.transactionReference)
      yield assertEquals(retrieved, Some(initialTransfer))

  test("update should modify an existing transfer's details"):
    withRepo: repo =>
      val updatedTransfer =
        initialTransfer.copy(status = Transfer.Status.SUCCESS)
      for
        _         <- repo.update(updatedTransfer)
        retrieved <- repo.select(initialTransfer.transactionReference)
      yield assertEquals(retrieved.map(_.status), Some(Transfer.Status.SUCCESS))

  test("update should do nothing if the transfer does not exist"):
    withRepo: repo =>
      val nonExistentTransfer =
        initialTransfer.copy(transactionReference = "nonExistentReference")
      for
        _         <- repo.update(nonExistentTransfer)
        retrieved <- repo.select(nonExistentTransfer.transactionReference)
      yield assertEquals(retrieved, None)

  test("delete should remove an existing transfer"):
    withRepo: repo =>
      for
        _         <- repo.delete(initialTransfer)
        retrieved <- repo.select(initialTransfer.transactionReference)
      yield assertEquals(retrieved, None)

  test("delete should do nothing if the transfer does not exist"):
    withRepo: repo =>
      val nonExistentTransfer =
        initialTransfer.copy(transactionReference = "nonExistentReference")
      for
        _         <- repo.delete(nonExistentTransfer)
        retrieved <- repo.select(nonExistentTransfer.transactionReference)
      yield assertEquals(retrieved, None)

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

  def testResource[F[_]: Sync]: Resource[F, TransfersRepo[F]] =
    Resource.eval(test)

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
