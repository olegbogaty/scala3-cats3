package srvc

import apis.model.{TransferErrorResponse, TransferResponse}
import cats.effect.{IO, Ref, Resource, Sync}
import cats.syntax.all.*
import data.domain.Transfer
import munit.CatsEffectSuite
import repo.TransfersRepoSuite
import srvc.PaymentGatewayServiceMock.PaymentGatewayServiceStrategy

class PaymentGatewayServiceSuite extends CatsEffectSuite:

  private val sampleTransfer = TransfersRepoSuite.testTransfer

  private val sampleTransactionRef =
    TransfersRepoSuite.testTransfer.transactionReference

  test("RejectTransfer should return a rejection response for enterTransfer"):
    for
      service <- PaymentGatewayServiceMock.test[IO](
        PaymentGatewayServiceStrategy.RejectTransfer
      )
      result <- service.enterTransfer(sampleTransfer)
    yield assertEquals(result, Left(TransferErrorResponse("transfer rejected")))

  test(
    "AlwaysPendingTransfer should return a pending response for enterTransfer"
  ):
    for
      service <- PaymentGatewayServiceMock.test[IO](
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
      service <- PaymentGatewayServiceMock.test[IO](
        PaymentGatewayServiceStrategy.SuccessTransfer
      )
      result <- service.checkTransferStatus(sampleTransactionRef)
    yield assertEquals(result, TransferResponse("SUCCESS"))

  test(
    "AlwaysPendingTransfer should return a pending response for checkTransferStatus"
  ):
    for
      service <- PaymentGatewayServiceMock.test[IO](
        PaymentGatewayServiceStrategy.AlwaysPendingTransfer
      )
      result <- service.checkTransferStatus(sampleTransactionRef)
    yield assertEquals(result, TransferResponse("PENDING"))

  test(
    "FailureTransfer should return a failure response for checkTransferStatus"
  ):
    for
      service <- PaymentGatewayServiceMock.test[IO](
        PaymentGatewayServiceStrategy.FailureTransfer
      )
      result <- service.checkTransferStatus(sampleTransactionRef)
    yield assertEquals(result, TransferResponse("FAILURE"))

  test("PendingThenSuccess should initially return PENDING and then SUCCESS"):
    for
      service <- PaymentGatewayServiceMock.test[IO](
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

object PaymentGatewayServiceMock:

  trait PaymentGatewayServiceStrategy
  object PaymentGatewayServiceStrategy:
    case object RejectTransfer        extends PaymentGatewayServiceStrategy
    case object AlwaysPendingTransfer extends PaymentGatewayServiceStrategy
    case object SuccessTransfer       extends PaymentGatewayServiceStrategy
    case object FailureTransfer       extends PaymentGatewayServiceStrategy
    case object PendingThenSuccess    extends PaymentGatewayServiceStrategy

  private def make[F[_]: Sync](
    strategy: PaymentGatewayServiceStrategy,
    state: Ref[F, Map[String, Transfer.Status]]
  ) =
    Sync[F].delay:
      new PaymentGatewayService[F]:
        override def enterTransfer(
          transfer: Transfer
        ): F[Either[TransferErrorResponse, TransferResponse]] =
          strategy match
            case PaymentGatewayServiceStrategy.RejectTransfer =>
              Left(TransferErrorResponse("transfer rejected")).pure[F]
            case _ =>
              state.update(
                _ + (transfer.transactionReference -> Transfer.Status.PENDING)
              ) *>
                Right(TransferResponse("transfer status: PENDING")).pure[F]

        override def checkTransferStatus(
          transactionReference: String
        ): F[TransferResponse] =
          strategy match
            case PaymentGatewayServiceStrategy.SuccessTransfer =>
              TransferResponse(Transfer.Status.SUCCESS.toString).pure[F]
            case PaymentGatewayServiceStrategy.AlwaysPendingTransfer =>
              TransferResponse(Transfer.Status.PENDING.toString).pure[F]
            case PaymentGatewayServiceStrategy.FailureTransfer |
                PaymentGatewayServiceStrategy.RejectTransfer =>
              TransferResponse(Transfer.Status.FAILURE.toString).pure[F]
            case PaymentGatewayServiceStrategy.PendingThenSuccess =>
              state.get
                .map(
                  _.getOrElse(transactionReference, Transfer.Status.FAILURE)
                )
                .map(_ => TransferResponse(Transfer.Status.SUCCESS.toString)) <*
                state.update(_ - transactionReference)

  def test[F[_]: Sync](
    strategy: PaymentGatewayServiceStrategy
  ): F[PaymentGatewayService[F]] =
    for
      state   <- Ref.of(Map.empty[String, Transfer.Status])
      service <- make(strategy, state)
    yield service

  def testResource[F[_]: Sync](
    strategy: PaymentGatewayServiceStrategy
  ): Resource[F, PaymentGatewayService[F]] =
    Resource.eval:
      test(strategy)
