package com.github.olegbogaty.oradian.repo

import cats.effect.*
import cats.syntax.all.*
import com.github.olegbogaty.oradian.data.domain.Account
import scribe.Scribe
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait AccountsRepo[F[_]]:
  def insert(account: Account): F[Unit]
  def select(id: Int): F[Option[Account]]
  def update(account: Account): F[Unit]
  def delete(account: Account): F[Unit]

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

  private val selectOne: Query[Int, Account] =
    sql"""
      SELECT account_id, bank_code, balance
      FROM   accounts
      WHERE  account_id = $int4
      """
      .query(int4 *: int4 *: numeric)
      .to[Account]

  private val updateOne: Command[(BigDecimal, Int)] =
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

  def makeResource[F[_]: Sync: Scribe](
    session: Session[F]
  ): Resource[F, AccountsRepo[F]] =
    Resource.eval:
      make(session)

  def make[F[_]: Sync: Scribe](session: Session[F]): F[AccountsRepo[F]] =
    Sync[F].delay:
      new AccountsRepo[F]:
        override def insert(account: Account): F[Unit] =
          Scribe[F].debug(s"insert account: $account") *>
            session.execute(insertOne)(account).void

        override def select(id: Int): F[Option[Account]] =
          Scribe[F].debug(s"select account by id: $id") *>
            session.option(selectOne)(id)

        override def update(account: Account): F[Unit] =
          Scribe[F].debug(s"update account: $account") *>
            session.execute(updateOne)(account.balance, account.accountId).void

        override def delete(account: Account): F[Unit] =
          Scribe[F].debug(s"delete account: $account") *>
            session.execute(deleteOne)(account.accountId).void
