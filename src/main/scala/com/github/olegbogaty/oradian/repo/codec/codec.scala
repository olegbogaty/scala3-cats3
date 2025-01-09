package com.github.olegbogaty.oradian.repo.codec

import skunk.Codec
import skunk.codec.all.timestamptz

import java.time.{Instant, OffsetDateTime, ZoneId}

val instant: Codec[Instant] =
  timestamptz.imap(_.toInstant)(OffsetDateTime.ofInstant(_, ZoneId.systemDefault()))
