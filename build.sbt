import Dependencies.*
import sbt.Keys.*

val scala3Version = "3.5.2"

resolvers += "jitpack" at "https://jitpack.io"

lazy val root = project
  .in(file("."))
  .settings(
    Compile / run / fork := true,
    name         := "scala3-cats3",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      ciris,
      cirisRefined,
      refinedCats,
      skunkCore,
      catsEffect,
      chimney,
      tapirCore,
      tapirCatsEffect,
      tapirNettyServerCats,
      tapirSwaggerUiBundle,
      tapirJsonCirce,
      scribeCats,
      slf4jNop,
      munit,
      munitCatsEffect,
      tapirSttpStubServer,
      sttpClient3Circe,
      scalatest,
      catsActors
    )
  )
