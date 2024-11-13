package srvc

import cats.effect.{Async, IO, Sync}
import cats.implicits.*
import conf.Config.TransferConfig
import conf.ConfigSuite
import eu.timepit.refined.types.numeric.PosInt
import munit.CatsEffectSuite

import scala.concurrent.duration.given

class TransferConfigServiceSuite extends CatsEffectSuite:

  private def withConfig[F[_]: Async]: F[TransferConfig] =
    for conf <- ConfigSuite.test
    yield conf.tc

  def withService[F[_]: Async]: F[TransferConfigService[F]] =
    for
      conf <- withConfig
      srvc <- TransferConfigServiceSuite.test(conf)
    yield srvc

  test("get should return the initial configuration"):
    withService[IO].flatMap: srvc =>
      srvc.get.flatMap: conf =>
        IO(conf).assertEquals(ConfigSuite.transferConfig)

  test("set method should update the configuration"):
    val newConfig =
      TransferConfig(tries = PosInt.unsafeFrom(5), delay = 5.seconds)
    withService[IO].flatMap: srvc =>
      for
        _         <- srvc.set(newConfig)
        updConfig <- srvc.get
      yield assertEquals(updConfig, newConfig)

object TransferConfigServiceSuite:
  def test[F[_]: Sync](init: TransferConfig): F[TransferConfigService[F]] =
    TransferConfigService.make[F](init)
