package conf

import apis.model.ConfigRequest
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

private given Conversion[Int, FiniteDuration] with
  def apply(i: Int): FiniteDuration = Duration(i, SECONDS)

final case class DbConfig(
  host: NonEmptyString,
  port: UserPortNumber,
  user: NonEmptyString,
  pass: NonEmptyString,
  base: NonEmptyString
)

final case class TransferConfig(
  tries: PosInt,
  delay: FiniteDuration
)

object TransferConfig:
  def fromRequest(request: ConfigRequest): TransferConfig =
    TransferConfig(request.tries, request.delay)

final case class ServerConfig(
  host: NonEmptyString,
  port: UserPortNumber
)

final case class HttpConfig(server: ServerConfig)

final case class AppConfig(
  db: DbConfig,
  tc: TransferConfig,
  http: HttpConfig
)

private def dbConfig[F[_]]: ConfigValue[F, DbConfig] =
  (
    env("DATABASE_HOST").as[NonEmptyString].default("localhost"),
    env("DATABASE_PORT").as[UserPortNumber].default(5454),
    env("DATABASE_USER").as[NonEmptyString].default("oradian"),
    env("DATABASE_PASS").as[NonEmptyString].default("oradian"),
    env("DATABASE_BASE").as[NonEmptyString].default("oradian")
  ).parMapN(DbConfig.apply)

private def transferConfig[F[_]]: ConfigValue[F, TransferConfig] =
  (
    env("TRANSFER_CONFIG_TRIES").as[PosInt].default(10),
    env("TRANSFER_CONFIG_DELAY").as[FiniteDuration].default(10.seconds)
  ).parMapN(TransferConfig.apply)

private def serverConfig[F[_]]: ConfigValue[F, ServerConfig] =
  (
    env("HTTP_SERVER_HOST").as[NonEmptyString].default("localhost"),
    env("HTTP_SERVER_PORT").as[UserPortNumber].default(8080)
  ).parMapN(ServerConfig.apply)

private def httpConfig[F[_]]: ConfigValue[F, HttpConfig] =
  serverConfig.map(HttpConfig.apply)

private def appConfig[F[_]]: ConfigValue[F, AppConfig] =
  (dbConfig, transferConfig, httpConfig).parMapN(AppConfig.apply)

def config[F[_]: Async]: F[AppConfig] = appConfig.load

object Config extends IOApp.Simple: // TODO remove
  def run: IO[Unit] =
    config[IO].flatMap(IO.println)
