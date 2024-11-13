package srvc

import cats.effect.Temporal
import cats.syntax.all.*

import scala.concurrent.duration.FiniteDuration

trait RetryLogicService[F[_]: Temporal]:
  def retry[A](fa: F[A], delay: FiniteDuration, tries: Int): F[A] =
    if (tries <= 1)
      fa
    else
      Temporal[F].sleep(delay) *> retry(fa, delay, tries - 1)

  def retry[A, B](
    fa: F[A],
    delay: FiniteDuration,
    tries: Int,
    onSuccess: F[A] => F[B],
    onFailure: F[A] => F[B]
  ): F[A] =
    if (tries <= 1)
      fa
    else
      Temporal[F].sleep(delay) *> retry(fa, delay, tries - 1)
