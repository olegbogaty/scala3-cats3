package srvc

trait RetryLogicService[F[_]]:
  def retry[A](fa: F[A]): F[Unit]

