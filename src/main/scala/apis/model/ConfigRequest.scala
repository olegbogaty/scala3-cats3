package apis.model

import scala.concurrent.duration.{Duration, FiniteDuration}

case class ConfigRequest(
  tries: Int,
  delayInSeconds: Int
)
