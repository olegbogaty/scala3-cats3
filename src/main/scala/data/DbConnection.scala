package data

import cats.effect.{Resource, Temporal}
import cats.effect.std.Console
import conf.DbConfig
import fs2.io.net.Network
import natchez.Trace
import skunk.Session

object DbConnection:
  def single[F[_] : Temporal : Trace : Network : Console](config: DbConfig): Resource[F, Session[F]] =
    Session.single(
      host = config.host.value,
      port = config.port.value,
      user = config.user.value,
      password = Some(config.pass.value),
      database = config.base.value
    )

  def pooled[F[_] : Temporal : Trace : Network : Console](config: DbConfig): Resource[F, Resource[F, Session[F]]] =
    Session.pooled(
      host = config.host.value,
      port = config.port.value,
      user = config.user.value,
      password = Some(config.pass.value),
      database = config.base.value,
      max = 10
    )

