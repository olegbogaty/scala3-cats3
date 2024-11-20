import Dependencies.ver.*
import sbt.*

object Dependencies {

  object ver {
    lazy val cirisVersion            = "3.6.0"
    lazy val skunkVersion            = "0.6.4"
    lazy val catsEffectVersion       = "3.5.5"
    lazy val chimneyVersion          = "1.5.0"
    lazy val tapirVersion            = "1.11.7"
    lazy val scribeVersion           = "3.15.2"
    lazy val munitVersion            = "1.0.0"
    lazy val munitCatsEffectVersion  = "2.0.0"
    lazy val sttpClient3CirceVersion = "3.9.8"
    lazy val scalatestVersion        = "3.2.19"
    lazy val catsActorsVersion       = "2.0.0-RC5"
  }

  // main
  lazy val ciris        = "is.cir"        %% "ciris"         % cirisVersion
  lazy val cirisRefined = "is.cir"        %% "ciris-refined" % cirisVersion
  lazy val skunkCore    = "org.tpolecat"  %% "skunk-core"    % skunkVersion
  lazy val catsEffect   = "org.typelevel" %% "cats-effect"   % catsEffectVersion
  lazy val chimney      = "io.scalaland"  %% "chimney"       % chimneyVersion

  // http
  lazy val tapirCore =
    "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion
  lazy val tapirCatsEffect =
    "com.softwaremill.sttp.tapir" %% "tapir-cats-effect" % tapirVersion
  lazy val tapirNettyServerCats =
    "com.softwaremill.sttp.tapir" %% "tapir-netty-server-cats" % tapirVersion
  lazy val tapirSwaggerUiBundle =
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion
  lazy val tapirJsonCirce =
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion

  // logs
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.5.8"
  lazy val scribe         = "com.outr"      %% "scribe"          % scribeVersion
  lazy val scribeCats     = "com.outr"      %% "scribe-cats"     % scribeVersion

  // test
  lazy val munit = "org.scalameta" %% "munit" % munitVersion % Test
  lazy val munitCatsEffect =
    "org.typelevel" %% "munit-cats-effect" % munitCatsEffectVersion % Test
  lazy val tapirSttpStubServer =
    "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion % Test
  lazy val sttpClient3Circe =
    "com.softwaremill.sttp.client3" %% "circe" % sttpClient3CirceVersion % Test
  lazy val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion % Test

  // todo
  lazy val catsActors =
    "com.github.suprnation.cats-actors" %% "cats-actors" % catsActorsVersion

}
