package apis.model

case class TransferRequest(
  senderAccount: Int,
  recipientBankCode: Int,
  recipientAccount: Int,
  amount: BigDecimal,
  transactionReference: String
)
