package repo

import cats.effect.*
import cats.syntax.all.*
import data.domain.Transfer
import data.domain.Transfer.Status
import data.domain.Transfer.Status.codec
import natchez.Trace.Implicits.noop
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import java.time.LocalDateTime

trait TransfersRepo[F[_]] {
  def insert(transfer: Transfer): F[Unit]
  def select(transfer: Transfer): F[Option[Transfer]]
  def update(transfer: Transfer): F[Unit]
  def delete(transfer: Transfer): F[Unit]
}

object TransfersRepo:
  private val insertOne: Command[Transfer] =
    sql"""
      INSERT INTO transfers (
        account_id,
        amount,
        status,
        recipient_account,
        recipient_bank_code,
        transaction_reference,
        transfer_date
      ) VALUES ($int4, $numeric, $codec, $int4, $int4, $varchar, $timestamp)
      """.command
      .to[Transfer]

  private val selectOne: Query[String, Transfer] =
    sql"""
      SELECT
        account_id,
        amount,
        status,
        recipient_account,
        recipient_bank_code,
        transaction_reference,
        transfer_date
      FROM   transfers
      WHERE  transaction_reference = $varchar
      """
      .query(int4 *: numeric *: codec *: int4 *: int4 *: varchar *: timestamp)
      .to[Transfer]

  private val updateOne: Command[(Status, String)] =
    sql"""
      UPDATE transfers
      SET    status = $codec
      WHERE  transaction_reference = $varchar
    """.command

  private val deleteOne: Command[String] =
    sql"""
      DELETE FROM transfers
      WHERE transaction_reference = $varchar
    """.command

  def make[F[_]: Sync](session: Session[F]): F[TransfersRepo[F]] =
    Sync[F].delay(
      new TransfersRepo[F]:
        override def insert(transfer: Transfer): F[Unit] =
          session.execute(insertOne)(transfer).void

        override def select(transfer: Transfer): F[Option[Transfer]] =
          session.option(selectOne)(transfer.transactionReference)

        override def update(transfer: Transfer): F[Unit] =
          session
            .execute(updateOne)(transfer.status, transfer.transactionReference)
            .void

        override def delete(transfer: Transfer): F[Unit] =
          session.execute(deleteOne)(transfer.transactionReference).void
    )

  def makeResource[F[_]: Sync](session: Session[F]): Resource[F, TransfersRepo[F]] =
    Resource.eval(make(session))

object TransfersRepoMain extends IOApp:
  val session: Resource[IO, Session[IO]] =
    Session.single( // (2)
      host = "localhost",
      port = 5454,
      user = "oradian",
      database = "oradian",
      password = Some("oradian")
    )

  def run(args: List[String]): IO[ExitCode] =
    val transfer = Transfer(
      1,
      BigDecimal(100.25),
      Transfer.Status.RUNNING,
      2,
      3,
      "ref",
      LocalDateTime.now
    )
    session
      .flatMap(TransfersRepo.makeResource[IO](_))
      .use: repo => // (3)
        for
          _      <- repo.delete(transfer)
          _      <- repo.insert(transfer)
          option <- repo.select(transfer)
          _      <- IO.println(option)
          _      <- repo.update(transfer.copy(status = Transfer.Status.SUCCESS))
          option <- repo.select(transfer)
          _      <- IO.println(option)
        yield ExitCode.Success
