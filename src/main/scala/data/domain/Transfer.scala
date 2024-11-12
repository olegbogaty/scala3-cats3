package data.domain

import apis.model.TransferRequest
import data.domain.Transfer.Status
import skunk.Codec

import java.time.LocalDateTime

case class Transfer(
  accountId: Int,
  amount: BigDecimal,
  status: Status,
  recipientAccount: Int,
  recipientBankCode: Int,
  transactionReference: String,
  transferDate: LocalDateTime
)

object Transfer:
  enum Status:
    case PENDING, SUCCESS, FAILURE

  object Status:
    given codec: Codec[Status] =
      Codec.simple(_.toString, Status.fromString, skunk.data.Type.varchar)

    private def fromString(status: String): Either[String, Status] =
      try Right(Status.valueOf(status))
      catch case _ => Left("UNKNOWN STATUS")

  import io.scalaland.chimney.dsl._
  def fromRequest(request: TransferRequest): Transfer =
    request.into[Transfer]
      .withFieldRenamed(_.senderAccount, _.accountId)
      .withFieldConst(_.transferDate, LocalDateTime.now)
      .withFieldConst(_.status, Status.PENDING)
      .transform

