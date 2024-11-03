package repo

import cats.Monad
import cats.effect.*
import cats.syntax.all.*
import data.domain.Account
import natchez.Trace.Implicits.noop
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait AccountsRepo[F[_]] {
  def insert(account: Account): F[Unit]
  def lookup(account: Account): F[Option[Account]]
  def update(account: Account): F[Unit]
  def delete(account: Account): F[Unit]
}

object AccountsRepo:
  private val insertOne: Command[Account] =
    sql"""
      INSERT INTO accounts (
          account_id,
          bank_code,
          balance
      ) VALUES ($int4, $int4, $numeric)
      """.command
      .to[Account]

  private val lookupOne: Query[Int, Account] =
    sql"""
      SELECT account_id, bank_code, balance
      FROM   accounts
      WHERE  account_id = $int4
      """
      .query(int4 *: int4 *: numeric(18, 2))
      .to[Account]

  private val updateOne =
    sql"""
      UPDATE accounts
      SET    balance = $numeric
      WHERE  account_id = $int4
    """.command

  private val deleteOne: Command[Int] =
    sql"""
      DELETE FROM accounts
      WHERE account_id = $int4
    """.command

  def make[F[_]: Monad](session: Session[F]): AccountsRepo[F] =
    new AccountsRepo[F]:
      override def insert(account: Account): F[Unit] =
        session.execute(insertOne)(account).void
      override def lookup(account: Account): F[Option[Account]] =
        session.option(lookupOne)(account.accountId)
      override def update(account: Account): F[Unit] =
        session.execute(updateOne)(account.balance, account.accountId).void
      override def delete(account: Account): F[Unit] =
        session.execute(deleteOne)(account.accountId).void

end AccountsRepo

object Main extends IOApp:
  val session: Resource[IO, Session[IO]] =
    Session.single( // (2)
      host = "localhost",
      port = 5454,
      user = "oradian",
      database = "oradian",
      password = Some("oradian")
    )

  def run(args: List[String]): IO[ExitCode] =
    val account = Account(123, 321, BigDecimal(100.25))
    session
      .map(AccountsRepo.make[IO](_))
      .use: repo => // (3)
        for
          _      <- repo.delete(account)
          _      <- repo.insert(account)
          option <- repo.lookup(account)
          _      <- IO.println(option)
          _      <- repo.update(account.copy(balance = account.balance - 50))
          option <- repo.lookup(account)
          _      <- IO.println(option)
        yield ExitCode.Success
