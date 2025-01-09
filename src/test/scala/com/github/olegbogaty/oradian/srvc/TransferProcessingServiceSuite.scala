package com.github.olegbogaty.oradian.srvc

import cats.effect.kernel.Concurrent
import cats.effect.{Async, IO, Temporal}
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import com.github.olegbogaty.oradian.conf.Config.TransferConfig
import com.github.olegbogaty.oradian.data.domain.{Account, Transfer}
import com.github.olegbogaty.oradian.logs.Log
import com.github.olegbogaty.oradian.mock.PaymentGatewayService
import com.github.olegbogaty.oradian.mock.PaymentGatewayService.PaymentGatewayServiceStrategy
import com.github.olegbogaty.oradian.repo.{AccountRepoSuite, TransfersRepoSuite}
import com.github.olegbogaty.oradian.srvc.model.TransferError
import munit.CatsEffectSuite
import munit.catseffect.IOFixture
import scribe.Level
import scribe.cats.given

import java.time.Instant
import scala.concurrent.duration.*

class TransferProcessingServiceSuite extends CatsEffectSuite:
  private val logLevel: IOFixture[Unit] = ResourceSuiteLocalFixture(
    "logLevel",
    Log.makeResource(Level.Warn)
  )
  private val testConfig   = TransferConfig.unsafeFrom(1, 1) // to speedup test
  private val validAccount = Account(1234567890, 333, 500.0)
  private val validTransfer = Transfer(
    accountId = 1234567890,
    recipientAccount = 999999999,
    recipientBankCode = 5555,
    amount = 100.0,
    transactionReference = "txn-001",
    status = Transfer.Status.PENDING,
    transferDate = Instant.now
  )

  override def munitFixtures: Seq[IOFixture[Unit]] = List(logLevel)

  private def makeService[F[_]: Async: Concurrent: Temporal](
    strategy: PaymentGatewayServiceStrategy
  ): F[TransferProcessingService[F]] =
    for
      accountRepo    <- AccountRepoSuite.test
      _              <- accountRepo.insert(validAccount)
      accountService <- AccountService.make(accountRepo)
      configService  <- TransferConfigService.make(testConfig)
      gateway        <- PaymentGatewayService.make(strategy)
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
      _      <- IO(assert(result.isRight, "Transfer should be accepted and pending"))
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
      _      <- IO.sleep(2.seconds) // Wait for retries
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
      _       <- IO.sleep(2.seconds) // Wait for retries
      status  <- service.checkTransferStatus(validTransfer.transactionReference)
      _ <- IO(
        assertEquals(
          status,
          Some(Transfer.Status.FAILURE),
          "Transfer should fail"
        )
      )
    yield ()

  test("Transfer should fail after reaching tries limit"):
    for
      service <- makeService[IO](
        PaymentGatewayServiceStrategy.AlwaysPendingTransfer
      )
      _      <- service.enterTransfer(validTransfer)
      _      <- IO.sleep(2.seconds) // Wait for retries to exhaust
      status <- service.checkTransferStatus(validTransfer.transactionReference)
      _ <- IO(
        assertEquals(
          status,
          Some(Transfer.Status.FAILURE),
          "Transfer should fail"
        )
      )
    yield ()

  test(
    "Transfer error for transfer with the same transaction reference, then success"
  ):
    for
      service <- makeService[IO](
        PaymentGatewayServiceStrategy.PendingThenSuccess
      )
      _ <- service.enterTransfer(validTransfer)
      statusPending <- service.checkTransferStatus(
        validTransfer.transactionReference
      )
      errorExists <- service.enterTransfer(validTransfer)
      _           <- IO.sleep(2.seconds) // Wait for retries
      statusSuccess <- service.checkTransferStatus(
        validTransfer.transactionReference
      )
    yield
      assertEquals(
        statusPending,
        Some(Transfer.Status.PENDING),
        "Transfer pending"
      )
      assert(errorExists.isLeft)
      assertEquals(
        errorExists.left.get,
        TransferError(
          s"transfer with transaction reference `${validTransfer.transactionReference}` already exists, status: ${statusPending.get}"
        ),
        "Transfer already exists"
      )
      assertEquals(
        statusSuccess,
        Some(Transfer.Status.SUCCESS),
        "Transfer success"
      )
