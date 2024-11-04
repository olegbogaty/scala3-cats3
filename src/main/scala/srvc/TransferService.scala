package srvc

import data.domain.Transfer

trait TransferService[F[_]]:
  def transfer(transfer: Transfer): F[Unit]
