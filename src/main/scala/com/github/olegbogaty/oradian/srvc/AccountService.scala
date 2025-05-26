package com.github.olegbogaty.oradian.srvc

import cats.effect.Sync
import cats.effect.kernel.Resource
import cats.implicits.*
import cats.syntax.all.*
import com.github.olegbogaty.oradian.data.domain.Account
import com.github.olegbogaty.oradian.repo.AccountsRepo
import io.scalaland.chimney.dsl.*
import scribe.Scribe

//  The account service should enable account lookup by number, balance querying, and balance updating
//  through enterWithdrawal. Since the focus is on outgoing transfers you can omit deposit methods.
trait AccountService[F[_]]:
  def lookup(accountId: Int): F[Option[Account]]
  def balance(accountId: Int): F[Option[BigDecimal]]
  def enterWithdrawal(account: Account, amount: BigDecimal): F[Unit]

object AccountService:
  def makeResource[F[_]: Sync: Scribe](
    accountsRepo: AccountsRepo[F]
  ): Resource[F, AccountService[F]] =
    Resource.eval:
      make(accountsRepo)

  def make[F[_]: Sync: Scribe](
    accountsRepo: AccountsRepo[F]
  ): F[AccountService[F]] =
    Sync[F].delay:
      new AccountService[F]:
        def lookup(accountId: Int): F[Option[Account]] =
          Scribe[F].debug(s"lookup account by id: $accountId") *>
            accountsRepo.select(accountId)
        def balance(accountId: Int): F[Option[BigDecimal]] =
          Scribe[F].debug(s"get account balance by id: $accountId") *>
            lookup(accountId).map(_.map(_.balance))
        def enterWithdrawal(account: Account, amount: BigDecimal): F[Unit] =
          val updatedAccount = account
            .into[Account]
            .withFieldComputed(_.balance, _.balance - amount)
            .transform
          Scribe[F].debug(
            s"enter withdrawal with amount $amount for account: $account"
          ) *>
            accountsRepo.update(updatedAccount) *> ().pure[F]
