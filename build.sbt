import sbt.Keys.libraryDependencies

val scala3Version = "3.5.2"

lazy val tapirVersion = "1.11.7"
resolvers += "jitpack" at "https://jitpack.io"

lazy val root = project
  .in(file("."))
  .settings(
    name := "oradian",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    libraryDependencies += "is.cir" %% "ciris" % "3.6.0",
    libraryDependencies += "is.cir" %% "ciris-refined" % "3.6.0",
    libraryDependencies += "org.tpolecat" %% "skunk-core" % "0.6.4",
    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.5.5",
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.7",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.8",
    // http
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-cats-effect" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-cats" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "com.softwaremill.sttp.client3" %% "circe" % "3.9.8" % Test
    ),
    libraryDependencies += "com.github.suprnation.cats-actors" %% "cats-actors" % "2.0.0-RC5"
//    libraryDependencies += "com.github.pureconfig" %% "pureconfig" % "0.17.7"
  )
