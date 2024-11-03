val scala3Version = "3.5.2"

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
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.7"
//    libraryDependencies += "com.github.pureconfig" %% "pureconfig" % "0.17.7"
  )
