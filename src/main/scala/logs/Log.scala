package logs

import cats.effect.*
import scribe.Scribe
import scribe.cats.*

trait Log[F[_]: Sync] { self =>
  val log = summon[Scribe[F]]
}
