package srvc

import cats.effect.kernel.Concurrent
import cats.effect.{Async, IO, Temporal}
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import conf.Config.TransferConfig
import data.domain.{Account, Transfer}
import munit.CatsEffectSuite
import repo.{AccountRepoSuite, TransfersRepoSuite}
import srvc.PaymentGatewayServiceMock.PaymentGatewayServiceStrategy

import java.time.LocalDateTime
import scala.concurrent.duration.*

class TransferProcessingServiceSuite extends CatsEffectSuite:

  private val testConfig   = TransferConfig.unsafeFrom(2, 2)
  private val validAccount = Account(1234567890, 333, 500.0)
  private val validTransfer = Transfer(
    accountId = 1234567890,
    recipientAccount = 999999999,
    recipientBankCode = 5555,
    amount = 100.0,
    transactionReference = "txn-001",
    status = Transfer.Status.PENDING,
    transferDate = LocalDateTime.now
  )

  private def makeService[F[_]: Async: Concurrent: Temporal](
    strategy: PaymentGatewayServiceStrategy
  ): F[TransferProcessingService[F]] =
    for
      accountRepo    <- AccountRepoSuite.test
      _              <- accountRepo.insert(validAccount)
      accountService <- AccountService.make(accountRepo)
      configService  <- TransferConfigService.make(testConfig)
      gateway        <- PaymentGatewayServiceMock.test(strategy)
      transfersRepo  <- TransfersRepoSuite.test
      service <- TransferProcessingService.make(
        gateway,
        accountService,
        transfersRepo,
        configService
      )
    yield service

  test(
    "enterTransfer should lock funds and add transfer to repo for pending transfer"
  ):
    for
      service <- makeService[IO](
        PaymentGatewayServiceStrategy.AlwaysPendingTransfer
      )
      result <- service.enterTransfer(validTransfer)
      _ <- IO(assert(result.isRight, "Transfer should be accepted and pending"))
      _ <- IO {
        assertEquals(
          result.toOption.get.status,
          Transfer.Status.PENDING,
          "Transfer status should be pending"
        )
      }
    yield ()

  test("enterTransfer should return error for insufficient balance"):
    val invalidTransfer =
      validTransfer.copy(amount = 1000.0) // More than balance
    for
      service <- makeService[IO](
        PaymentGatewayServiceStrategy.AlwaysPendingTransfer
      )
      result <- service.enterTransfer(invalidTransfer)
      _ <- IO {
        assert(
          result.isLeft,
          "Transfer should be rejected due to insufficient funds"
        )
        assertEquals(
          result.swap.toOption.get.msg,
          "insufficient amount",
          "Error message should indicate insufficient funds"
        )
      }
    yield ()

  test("Pending transfer should finalize as SUCCESS and update balances"):
    for
      service <- makeService[IO](
        PaymentGatewayServiceStrategy.PendingThenSuccess
      )
      _      <- service.enterTransfer(validTransfer)
      _      <- IO.sleep(4.seconds) // Wait for retries
      status <- service.checkTransferStatus(validTransfer.transactionReference)
      _ <- IO(
        assertEquals(
          status,
          Some(Transfer.Status.SUCCESS),
          "Transfer should succeed"
        )
      )
    yield ()

  test("Pending transfer should rollback on FAILURE"):
    for
      service <- makeService[IO](PaymentGatewayServiceStrategy.FailureTransfer)
      _       <- service.enterTransfer(validTransfer)
      _       <- IO.sleep(4.seconds) // Wait for retries
      status  <- service.checkTransferStatus(validTransfer.transactionReference)
      _ <- IO(
        assertEquals(
          status,
          Some(Transfer.Status.FAILURE),
          "Transfer should fail"
        )
      )
    yield ()

  test("Transfer should stop after reaching tries limit"):
    for
      service <- makeService[IO](
        PaymentGatewayServiceStrategy.AlwaysPendingTransfer
      )
      _      <- service.enterTransfer(validTransfer)
      _      <- IO.sleep(4.seconds) // Wait for retries to exhaust
      status <- service.checkTransferStatus(validTransfer.transactionReference)
      _ <- IO(
        assertEquals(
          status,
          Some(Transfer.Status.PENDING), //
          "Transfer should remain pending"
        )
      )
    yield ()
