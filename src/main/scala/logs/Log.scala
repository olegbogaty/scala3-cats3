package logs

import cats.effect.*
import scribe.Scribe
import scribe.cats.*

trait Log[F[_]: Sync]:
  val log: Scribe[F] = summon[Scribe[F]]
