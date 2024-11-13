package data.domain

import cats.syntax.all.*
import skunk.*
import skunk.codec.all.*
import skunk.syntax.all.*

// For simplicity, the account model should track the account’s balance and other necessary minimal information.
case class Account(accountId: Int, bankCode: Int, balance: BigDecimal)

object Account:
  val dbCodec: Codec[Account] =
    (int4, int4, numeric).tupled.imap { case (accountId, bankCode, balance) =>
      Account(accountId, bankCode, balance)
    } { account => (account.accountId, account.bankCode, account.balance) }
