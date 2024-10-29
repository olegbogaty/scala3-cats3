package data.domain

// For simplicity, the account model should track the account’s balance and other necessary minimal information.
case class Account(accountId: Int, bankCode: Int, balance: BigDecimal)
