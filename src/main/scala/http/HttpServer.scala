package http

import cats.*
import cats.effect.*
import cats.effect.kernel.Resource
import cats.effect.std.{Console, Dispatcher}
import cats.syntax.all.*
import conf.Config.ServerConfig
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.cats.{NettyCatsServer, NettyCatsServerBinding}
import sttp.tapir.swagger.bundle.SwaggerInterpreter

trait HttpServer[F[_]]:
  def serve(): F[Unit]

object HttpServer:
  def makeResource[F[_]: Async: Console](
    config: ServerConfig,
    endpoints: List[ServerEndpoint[Any, F]]
  ): Resource[F, HttpServer[F]] =
    for
      server <- bindServer(config, endpoints)
      handle <- Resource.eval:
        for
          _ <- Console[F].println(
            s"Go to http://localhost:${config.port.value}/docs to open SwaggerUI"
          )
          _ <- server.start()
        yield new HttpServer[F]:
          override def serve(): F[Unit] = ().pure[F]
    yield handle

  private def bindServer[F[_]: Async](
    config: ServerConfig,
    endpoints: List[ServerEndpoint[Any, F]]
  ): Resource[F, NettyCatsServer[F]] =
    makeServer.map:
      _.host(config.host.value)
        .port(config.port.value)
        .addEndpoints(endpoints)
        .addEndpoints(docs(endpoints))

  private def makeServer[F[_]: Async]: Resource[F, NettyCatsServer[F]] =
    Dispatcher
      .parallel[F]
      .map(NettyCatsServer[F](_))

//  def make[F[_]: Async: Console](
//    config: ServerConfig,
//    endpoints: List[ServerEndpoint[Any, F]]
//  ): F[HttpServer[F]] =
//    bindServer(config, endpoints)
//      .use: server =>
//        for
//          _ <- Console[F].println(
//            s"Go to http://localhost:${config.port.value}/docs to open SwaggerUI"
//          )
//        yield new HttpServer[F]:
//          override def serve(): F[Unit] = server.start().void

  private def docs[F[_]](
    endpoints: List[ServerEndpoint[Any, F]]
  ): List[ServerEndpoint[Any, F]] =
    SwaggerInterpreter()
      .fromServerEndpoints[F](endpoints, "docs", "1.0.0")
