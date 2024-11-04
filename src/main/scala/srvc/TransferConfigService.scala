package srvc

import cats.effect.{ExitCode, IO, IOApp, Ref, Resource, Sync}
import conf.{TransferConfig, config}
import cats.*
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.PosInt

trait TransferConfigService[F[_]] {
  def set(config: TransferConfig): F[Unit]
  def get: F[TransferConfig]
}

object TransferConfigService extends IOApp:
  def make[F[_] : Sync](init: TransferConfig): F[TransferConfigService[F]] =
    for
      config <- Ref[F].of(init)
      service <- make(config)
    yield service

  def make[F[_] : Sync](init: Ref[F, TransferConfig]): F[TransferConfigService[F]] =
    Sync[F].delay:
      new TransferConfigService[F]:
        override def set(config: TransferConfig): F[Unit] =
          init.set(config)
        override def get: F[TransferConfig] =
          init.get

  def makeResource[F[_]: Sync](init: TransferConfig): Resource[F, TransferConfigService[F]] =
    Resource.eval(make(init))

  def makeResource[F[_]: Sync](init: Ref[F, TransferConfig]): Resource[F, TransferConfigService[F]] =
    Resource.eval(make(init))

  import scala.concurrent.duration.*
  val newConfig = TransferConfig(
    tries = PosInt.unsafeFrom(123),
    delay = 18.seconds
  )

  def run(args: List[String]): IO[ExitCode] =
    (for
      config <- config[IO]
      tcService <- make[IO](config.tc)
      initConfig <- tcService.get
      _ <- IO.println(initConfig)
      _ <- tcService.set(newConfig)
      initConfig <- tcService.get
      _ <- IO.println(initConfig)
    yield tcService).as(ExitCode.Success)