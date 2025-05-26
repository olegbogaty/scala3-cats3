package com.github.olegbogaty.oradian.srvc

import cats.effect.{Async, IO, Sync}
import cats.implicits.*
import com.github.olegbogaty.oradian.conf.Config.TransferConfig
import com.github.olegbogaty.oradian.conf.ConfigSuite
import com.github.olegbogaty.oradian.logs.Log
import eu.timepit.refined.types.numeric.PosInt
import munit.CatsEffectSuite
import munit.catseffect.IOFixture
import scribe.Level
import scribe.cats.given

import scala.concurrent.duration.given

class TransferConfigServiceSuite extends CatsEffectSuite:

  private val logLevel: IOFixture[Unit] = ResourceSuiteLocalFixture(
    "logLevel",
    Log.makeResource(Level.Warn)
  )

  override def munitFixtures: Seq[IOFixture[Unit]] = List(logLevel)

  def withService[F[_]: Async]: F[TransferConfigService[F]] =
    for
      conf <- withConfig
      srvc <- TransferConfigServiceSuite.test(conf)
    yield srvc

  private def withConfig[F[_]: Async]: F[TransferConfig] =
    for conf <- ConfigSuite.test
    yield conf.tc

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
