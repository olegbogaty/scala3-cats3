package mock

import apis.model.{TransferErrorResponse, TransferResponse}
import cats.effect.{IO, Ref, Resource, Sync}
import cats.syntax.all.*
import data.domain.Transfer
import mock.PaymentGatewayService
import mock.PaymentGatewayService.PaymentGatewayServiceStrategy
import munit.CatsEffectSuite
import repo.TransfersRepoSuite

class PaymentGatewayServiceSuite extends CatsEffectSuite:

  private val sampleTransfer = TransfersRepoSuite.testTransfer

  private val sampleTransactionRef =
    TransfersRepoSuite.testTransfer.transactionReference

  test("RejectTransfer should return a rejection response for enterTransfer"):
    for
      service <- PaymentGatewayService.make[IO](
        PaymentGatewayServiceStrategy.RejectTransfer
      )
      result <- service.enterTransfer(sampleTransfer)
    yield assertEquals(result, Left(TransferErrorResponse("transfer rejected")))

  test(
    "AlwaysPendingTransfer should return a pending response for enterTransfer"
  ):
    for
      service <- PaymentGatewayService.make[IO](
        PaymentGatewayServiceStrategy.AlwaysPendingTransfer
      )
      result <- service.enterTransfer(sampleTransfer)
    yield assertEquals(
      result,
      Right(TransferResponse("transfer status: PENDING"))
    )

  test(
    "SuccessTransfer should return a success response for checkTransferStatus"
  ):
    for
      service <- PaymentGatewayService.make[IO](
        PaymentGatewayServiceStrategy.SuccessTransfer
      )
      result <- service.checkTransferStatus(sampleTransactionRef)
    yield assertEquals(result, TransferResponse("SUCCESS"))

  test(
    "AlwaysPendingTransfer should return a pending response for checkTransferStatus"
  ):
    for
      service <- PaymentGatewayService.make[IO](
        PaymentGatewayServiceStrategy.AlwaysPendingTransfer
      )
      result <- service.checkTransferStatus(sampleTransactionRef)
    yield assertEquals(result, TransferResponse("PENDING"))

  test(
    "FailureTransfer should return a failure response for checkTransferStatus"
  ):
    for
      service <- PaymentGatewayService.make[IO](
        PaymentGatewayServiceStrategy.FailureTransfer
      )
      pending <- service.enterTransfer(sampleTransfer)
      failure <- service.checkTransferStatus(sampleTransactionRef)
    yield
      assertEquals(pending, Right(TransferResponse("transfer status: PENDING")))
      assertEquals(failure, TransferResponse("FAILURE"))

  test("PendingThenSuccess should initially return PENDING and then SUCCESS"):
    for
      service <- PaymentGatewayService.make[IO](
        PaymentGatewayServiceStrategy.PendingThenSuccess
      )
      pending <- service.enterTransfer(sampleTransfer)
      success <- service.checkTransferStatus(sampleTransactionRef)
    yield
      assertEquals(pending, Right(TransferResponse("transfer status: PENDING")))
      assertEquals(
        success,
        TransferResponse("SUCCESS")
      )
