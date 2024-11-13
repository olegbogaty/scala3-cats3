package srvc

import cats.effect.Sync
import cats.effect.kernel.Resource
import cats.implicits.*
import cats.syntax.all.*
import data.domain.Account
import io.scalaland.chimney.dsl.*
import repo.{AccountsRepo, TransfersRepo}

//  The account service should enable account lookup by number, balance querying, and balance updating
//  through enterWithdrawal. Since the focus is on outgoing transfers you can omit deposit methods.
trait AccountService[F[_]]:
  def lookup(accountId: Int): F[Option[Account]]
  def balance(accountId: Int): F[Option[BigDecimal]]
  def enterWithdrawal(account: Account, amount: BigDecimal): F[Account]

object AccountService:
  def makeResource[F[_]: Sync](
    accountsRepo: AccountsRepo[F]
  ): Resource[F, AccountService[F]] =
    Resource.eval:
      make(accountsRepo)

  def make[F[_]: Sync](
    accountsRepo: AccountsRepo[F]
  ): F[AccountService[F]] =
    Sync[F].delay:
      new AccountService[F]:
        def lookup(accountId: Int): F[Option[Account]] =
          accountsRepo.select(accountId)
        def balance(accountId: Int): F[Option[BigDecimal]] =
          lookup(accountId).map(_.map(_.balance))
        def enterWithdrawal(account: Account, amount: BigDecimal): F[Account] =
          val updatedAccount = account
            .into[Account]
            .withFieldComputed(_.balance, _.balance - amount)
            .transform
          accountsRepo.update(updatedAccount) *> updatedAccount.pure[F]
