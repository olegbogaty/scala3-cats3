package conf

import cats.effect.{Async, IO, IOApp}
import cats.implicits.given
import cats.syntax.all.*
import ciris.*
import ciris.refined.*
import eu.timepit.refined.*
import eu.timepit.refined.api.*
import eu.timepit.refined.auto.*
import eu.timepit.refined.types.all.*
import eu.timepit.refined.types.net.UserPortNumber
import eu.timepit.refined.types.string.NonEmptyString

import scala.concurrent.duration.*

private given Conversion[String, NonEmptyString] with
  def apply(s: String): NonEmptyString = NonEmptyString.unsafeFrom(s)

private given Conversion[Int, UserPortNumber] with
  def apply(i: Int): UserPortNumber = UserPortNumber.unsafeFrom(i)

private given Conversion[Int, PosInt] with
  def apply(i: Int): PosInt = PosInt.unsafeFrom(i)

final case class DbConfig(
  host: NonEmptyString,
  port: UserPortNumber,
  user: NonEmptyString,
  pass: NonEmptyString
)

final case class TransferConfig(
  tries: PosInt,
  delay: FiniteDuration
)

final case class AppConfig(
  db: DbConfig,
  tc: TransferConfig
)

private def dbConfig[F[_]]: ConfigValue[F, DbConfig] =
  (
    env("DATABASE_HOST").as[NonEmptyString].default("localhost"),
    env("DATABASE_PORT").as[UserPortNumber].default(5454),
    env("DATABASE_USER").as[NonEmptyString].default("oradian"),
    env("DATABASE_PASS").as[NonEmptyString].default("oradian")
  ).parMapN(DbConfig.apply)

private def transferConfig[F[_]]: ConfigValue[F, TransferConfig] =
  (
    env("TRANSFER_CONFIG_TRIES").as[PosInt].default(10),
    env("TRANSFER_CONFIG_DELAY").as[FiniteDuration].default(10.seconds)
  ).parMapN(TransferConfig.apply)

private def appConfig[F[_]]: ConfigValue[F, AppConfig] =
  (dbConfig, transferConfig).parMapN(AppConfig.apply)

def config[F[_]: Async]: F[AppConfig] = appConfig.load

object Config extends IOApp.Simple:
  def run: IO[Unit] =
    config[IO].flatMap(IO.println)
