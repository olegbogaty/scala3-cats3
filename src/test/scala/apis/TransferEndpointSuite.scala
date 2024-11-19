package apis

import apis.model.{TransferErrorResponse, TransferRequest, TransferResponse, TransferStatusRequest}
import cats.effect.kernel.Resource
import cats.effect.std.Console
import cats.effect.{Async, IO}
import conf.Config
import data.domain.Account
import http.HttpServer
import io.circe
import io.circe.generic.auto.*
import munit.CatsEffectSuite
import munit.catseffect.IOFixture
import repo.{AccountRepoSuite, TransfersRepoSuite}
import srvc.PaymentGatewayServiceMock.PaymentGatewayServiceStrategy
import srvc.{AccountService, PaymentGatewayServiceMock, TransferConfigService, TransferService}
import sttp.client3.*
import sttp.client3.circe.*
import sttp.model.StatusCode

class TransferEndpointSuite extends CatsEffectSuite:

  private val validTransferRequest = TransferRequest(
    senderAccount = 1234567890,
    recipientBankCode = 12345,
    recipientAccount = 1234567891,
    amount = 100,
    transactionReference = "transactionReference"
  )

  private val invalidTransferRequest = TransferRequest(
    senderAccount = 1234567890,
    recipientBankCode = 12345,
    recipientAccount = 1234567890, // Same as sender
    amount = -50.0,                // Invalid amount
    transactionReference = "transactionReference"
  )

  private val initialAccount =
    Account(validTransferRequest.senderAccount, bankCode = 54321, 1000)

  def makeServer[F[_]: Async: Console]: Resource[F, HttpServer[F]] =
    for
      config       <- Config.makeResource
      accountRepo  <- AccountRepoSuite.testResource
      _            <- Resource.eval(accountRepo.insert(initialAccount))
      transferRepo <- TransfersRepoSuite.testResource
      paymentGatewayService <- PaymentGatewayServiceMock
        .testResource(PaymentGatewayServiceStrategy.SuccessTransfer)
      transferConfigService <- TransferConfigService.makeResource(
        config.tc
      )
      accountService <- AccountService.makeResource(accountRepo)
      transferService <- TransferService.makeResource(
        accountService,
        transferRepo,
        paymentGatewayService,
        transferConfigService
      )
      routes <- TransferEndpoint.makeResource(transferService)
      server <- http.HttpServer.makeResource(config.http.server, routes)
    yield server

  val httpServer: IOFixture[HttpServer[IO]] = ResourceSuiteLocalFixture(
    "http-server",
    makeServer[IO]
  )

  override def munitFixtures: Seq[IOFixture[HttpServer[IO]]] = List(httpServer)

  def withServer[T](
    test: SttpBackend[Identity, Any] => IO[T]
  ): IO[Unit] =
    test(HttpURLConnectionBackend()).void

  test("POST /enter-transfer with valid data should return success response") {
    for _ <- withServer { backend =>
        val res = basicRequest
          .post(uri"http://localhost:8080/enter-transfer")
          .body(validTransferRequest)
          .response(asJson[TransferResponse])
          .send(backend)
        IO {
          assertEquals(res.code, StatusCode.Ok)
          assertEquals(
            res.body,
            Right(TransferResponse("transfer status: PENDING"))
          )
        }
      }
    yield ()
  }

  test("POST /enter-transfer with invalid data should return error response"):
    for _ <- withServer { backend =>
        val res: Identity[Response[
          Either[ResponseException[String, circe.Error], TransferErrorResponse]
        ]] = basicRequest
          .post(uri"http://localhost:8080/enter-transfer")
          .body(invalidTransferRequest)
          .response(asJson[TransferErrorResponse])
          .send(backend)
        IO {
          assertEquals(res.code, StatusCode.BadRequest)
          assert(res.body.isLeft)
          println(res.body.left.map(_.getMessage))
          assert(
            res.body.left.get.getMessage // didn't find the right way to check exception
              .contains(
                "amount should be more then 0, sender account number should not be a recipient account number"
              )
          )
        }
      }
    yield ()

  test("POST /check-transfer-status should return the correct status"):
    val statusRequest = TransferStatusRequest("valid-transaction-id")
    for _ <- withServer { backend =>
        val res = basicRequest
          .post(uri"http://localhost:8080/check-transfer-status")
          .body(statusRequest)
          .response(asJson[TransferResponse])
          .send(backend)
        IO {
          assertEquals(res.code, StatusCode.Ok)
          assertEquals(
            res.body,
            Right(TransferResponse("transfer status: None"))
          )
        }
      }
    yield ()
