package srvc

import cats.*
import cats.effect.*
import cats.syntax.all.*
import conf.{TransferConfig, config}
import eu.timepit.refined.types.numeric.PosInt
import logs.Log

trait TransferConfigService[F[_]] extends Log[F]:
  def set(config: TransferConfig): F[Unit]
  def get: F[TransferConfig]

//  Transfer processing configuration service implementation
//  – The service should offer settings for:
//    ∗ tries -the maximum number of status checks for a transfer, e.g., 10
//    ∗ delay - the interval between status checks, e.g., 10 seconds
//  – Include a method for updating these settings, accessible via the /config-transfer endpoint.
//  – Maintain a single service instance throughout the runtime.
object TransferConfigService:
  def make[F[_]: Sync](init: TransferConfig): F[TransferConfigService[F]] =
    for
      config  <- Ref[F].of(init)
      service <- make(config)
    yield service

  def make[F[_]: Sync](
    init: Ref[F, TransferConfig]
  ): F[TransferConfigService[F]] =
    Sync[F].delay:
      new TransferConfigService[F]:
        override def set(config: TransferConfig): F[Unit] =
          log.info(s"received new transfer config: $config") *>
            init.set(config)
        override def get: F[TransferConfig] =
          init.get

  def makeResource[F[_]: Sync](
    init: TransferConfig
  ): Resource[F, TransferConfigService[F]] =
    Resource.eval(make(init))

  def makeResource[F[_]: Sync](
    init: Ref[F, TransferConfig]
  ): Resource[F, TransferConfigService[F]] =
    Resource.eval(make(init))

object TransferConfigServiceMain extends IOApp: // TODO remove
  import TransferConfigService.*

  import scala.concurrent.duration.*
  private val newConfig = TransferConfig(
    tries = PosInt.unsafeFrom(123),
    delay = 18.seconds
  )

  def run(args: List[String]): IO[ExitCode] =
    (for
      config     <- config[IO]
      tcService  <- make[IO](config.tc)
      initConfig <- tcService.get
      _          <- IO.println(initConfig)
      _          <- tcService.set(newConfig)
      initConfig <- tcService.get
      _          <- IO.println(initConfig)
    yield tcService).as(ExitCode.Success)
