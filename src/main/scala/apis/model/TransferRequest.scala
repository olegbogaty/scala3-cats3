package apis.model

case class TransferRequest(
  senderAccount: Int,
  recipientBankCode: Int,
  recipientAccount: Int,
  amount: BigDecimal,
  transactionReference: String
)

object TransferRequest:
  private[apis] val transferRequestExample =
    TransferRequest(
      senderAccount = 1111,
      recipientBankCode = 5555,
      recipientAccount = 2222,
      amount = 500.25,
      transactionReference = "unique transaction reference"
    )
