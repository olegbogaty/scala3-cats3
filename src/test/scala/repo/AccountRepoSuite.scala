package repo

import cats.effect.{Resource, Sync}
import cats.implicits.*
import data.domain.Account

import scala.collection.concurrent.TrieMap

class AccountRepoSuite {}

object AccountRepoSuite:
  val testAccount: Account = Account(0, 1, BigDecimal(1000))

  def testResource[F[_]: Sync]: Resource[F, AccountsRepo[F]] =
    Resource.eval(test)

  def test[F[_]: Sync]: F[AccountsRepo[F]] =
    Sync[F].delay:
      new AccountsRepo[F]:
        private val accounts =
          TrieMap[Int, Account](testAccount.accountId -> testAccount)

        def insert(account: Account): F[Unit] =
          Sync[F]
            .delay:
              accounts.put(account.accountId, account)
            .void

        def select(id: Int): F[Option[Account]] =
          Sync[F].delay:
            accounts.get(id)

        def update(account: Account): F[Unit] =
          Sync[F].delay:
            accounts.get(account.accountId) match
              case Some(_) => accounts.update(account.accountId, account)
              case None    => ()

        def delete(account: Account): F[Unit] =
          Sync[F]
            .delay:
              accounts.remove(account.accountId)
            .void
