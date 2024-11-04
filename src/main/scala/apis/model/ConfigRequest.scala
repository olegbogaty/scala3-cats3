package apis.model

import scala.concurrent.duration.{Duration, FiniteDuration}

//  Request should include fields for:
//    ∗ retry delay
//    ∗ number of attempts
case class ConfigRequest(
  tries: Int,
  delay: Int // in seconds
)
