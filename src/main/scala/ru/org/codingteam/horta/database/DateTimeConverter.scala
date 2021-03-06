package ru.org.codingteam.horta.database

import java.sql.Timestamp

import org.joda.time.DateTime

object DateTimeConverter {

  implicit def toTimestamp(dateTime: DateTime): Timestamp = {
    new Timestamp(dateTime.getMillis)
  }

}
