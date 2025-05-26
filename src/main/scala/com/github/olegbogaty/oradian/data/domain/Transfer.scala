package com.github.olegbogaty.oradian.data.domain

import com.github.olegbogaty.oradian.apis.model.TransferRequest
import com.github.olegbogaty.oradian.data.domain.Transfer.Status
import io.scalaland.chimney.dsl.*
import skunk.Codec

import java.time.Instant

case class Transfer(
  accountId: Int,
  amount: BigDecimal,
  status: Status,
  recipientAccount: Int,
  recipientBankCode: Int,
  transactionReference: String,
  transferDate: Instant
)

object Transfer:
  def fromRequest(request: TransferRequest): Transfer =
    request
      .into[Transfer]
      .withFieldRenamed(_.senderAccount, _.accountId)
      .withFieldConst(_.transferDate, Instant.now)
      .withFieldConst(_.status, Status.PENDING)
      .transform

  enum Status:
    case PENDING, SUCCESS, FAILURE

  object Status:
    val status: Codec[Status] =
      Codec.simple(_.toString, Status.fromString, skunk.data.Type.varchar)

    private def fromString(status: String): Either[String, Status] =
      try Right(Status.valueOf(status))
      catch case _ => Left("UNKNOWN STATUS")
