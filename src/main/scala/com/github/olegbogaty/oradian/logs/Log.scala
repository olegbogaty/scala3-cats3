package com.github.olegbogaty.oradian.logs

import cats.effect.*
import cats.syntax.all.*
import scribe.filter.{packageName, select}
import scribe.format.*
import scribe.handler.LogHandler
import scribe.{Level, Logger, Priority}

object Log:

  private val formatter: Formatter =
    formatter"$date $level ($threadName) $positionAbbreviated $newLine $messages"

  def makeResource[F[_]: Sync](logLevel: Level): Resource[F, Unit] =
    Resource.eval:
      make(logLevel)

  def make[F[_]: Sync](logLevel: Level): F[Unit] =
    Sync[F]
      .delay:
        val consoleHandler = LogHandler(
          minimumLevel = Some(logLevel),
          formatter = formatter,
          modifiers = List(
            select(
              packageName.startsWith("com.github.olegbogaty.oradian")
            ).boosted(logLevel, logLevel).priority(Priority.Important)
          )
        )

        Logger.root
          .orphan()
          .clearHandlers()
          .clearModifiers()
          .withHandler(consoleHandler)
          .replace()

        scribe
          .Logger()
          .orphan()
          .clearHandlers()
          .clearModifiers()
          .withHandler(consoleHandler)
          .replace()
      .void
