package srvc

import cats.effect.{IO, Sync}
import cats.implicits.*
import munit.CatsEffectSuite
import repo.AccountRepoSuite
import repo.AccountRepoSuite.testAccount

class AccountServiceSuite extends CatsEffectSuite:

  private def createService[F[_]: Sync]: F[AccountService[F]] =
    for
      repo <- AccountRepoSuite.test[F]
      _    <- repo.insert(testAccount)
      srvc <- AccountService.make[F](repo)
    yield srvc

  test("lookup should return account when it exists"):
    createService[IO].flatMap: srvc =>
      srvc
        .lookup(testAccount.accountId)
        .flatMap: result =>
          IO(assert(result.isDefined, "Expected account to be found")) *>
            IO(result.get).assertEquals(testAccount)

  test("balance should return the exact balance for an account by accountId"):
    createService[IO].flatMap: srvc =>
      srvc
        .balance(testAccount.accountId)
        .flatMap: result =>
          IO(assert(result.isDefined, "Expected balance to be found")) *>
            IO(result.get).assertEquals(testAccount.balance)

  test("enterWithdrawal should update account balance by specified amount"):
    val withdrawalAmount = BigDecimal(200)
    createService[IO].flatMap: srvc =>
      for
        account        <- srvc.lookup(testAccount.accountId).map(_.get)
        updatedAccount <- srvc.enterWithdrawal(account, withdrawalAmount)
        updatedBalance <- srvc.balance(testAccount.accountId)
      yield
        assertEquals(updatedAccount.balance, account.balance - withdrawalAmount)
        assertEquals(updatedBalance.get, account.balance - withdrawalAmount)

  test(
    "enterWithdrawal should update account balance by minus amount (in case of rollback transfer)"
  ):
    val withdrawalAmount = -BigDecimal(500) // unary - for amount
    createService[IO].flatMap: srvc =>
      for
        account        <- srvc.lookup(testAccount.accountId).map(_.get)
        updatedAccount <- srvc.enterWithdrawal(account, withdrawalAmount)
        updatedBalance <- srvc.balance(testAccount.accountId)
      yield
        assertEquals(updatedAccount.balance, account.balance - withdrawalAmount)
        assertEquals(updatedBalance.get, account.balance - withdrawalAmount)
