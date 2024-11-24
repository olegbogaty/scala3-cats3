package apis.model

case class TransferStatusRequest(transactionReference: String)

object TransferStatusRequest:
  private[apis] val example =
    TransferStatusRequest("unique transaction reference")
