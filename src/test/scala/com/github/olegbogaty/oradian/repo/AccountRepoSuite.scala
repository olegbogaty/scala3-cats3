package com.github.olegbogaty.oradian.repo

import cats.effect.{IO, Resource, Sync}
import cats.implicits.*
import com.github.olegbogaty.oradian.data.domain.Account
import com.github.olegbogaty.oradian.repo.AccountRepoSuite.testAccount
import munit.CatsEffectSuite

import scala.collection.concurrent.TrieMap

class AccountRepoSuite extends CatsEffectSuite:

  private val initialAccount = testAccount

  def withRepo(testCode: AccountsRepo[IO] => IO[Unit]): IO[Unit] =
    AccountRepoSuite.test[IO].flatMap(testCode)

  test("insert should store a new account"):
    withRepo: repo =>
      val newAccount = Account(1, 2, BigDecimal(2000))
      for
        _         <- repo.insert(newAccount)
        retrieved <- repo.select(newAccount.accountId)
      yield assertEquals(retrieved, Some(newAccount))

  test("select should retrieve an existing account"):
    withRepo: repo =>
      for retrieved <- repo.select(initialAccount.accountId)
      yield assertEquals(retrieved, Some(initialAccount))

  test("update should modify an existing account's details"):
    withRepo: repo =>
      val updatedAccount = initialAccount.copy(balance = BigDecimal(1500))
      for
        _         <- repo.update(updatedAccount)
        retrieved <- repo.select(initialAccount.accountId)
      yield assertEquals(retrieved.map(_.balance), Some(BigDecimal(1500)))

  test("update should do nothing if the account does not exist"):
    withRepo: repo =>
      val nonExistentAccount = Account(999, 3, BigDecimal(3000))
      for
        _         <- repo.update(nonExistentAccount)
        retrieved <- repo.select(nonExistentAccount.accountId)
      yield assertEquals(retrieved, None)

  test("delete should remove an existing account"):
    withRepo: repo =>
      for
        _         <- repo.delete(initialAccount)
        retrieved <- repo.select(initialAccount.accountId)
      yield assertEquals(retrieved, None)

  test("delete should do nothing if the account does not exist"):
    withRepo: repo =>
      val nonExistentAccount = Account(999, 3, BigDecimal(3000))
      for
        _         <- repo.delete(nonExistentAccount)
        retrieved <- repo.select(nonExistentAccount.accountId)
      yield assertEquals(retrieved, None)

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
