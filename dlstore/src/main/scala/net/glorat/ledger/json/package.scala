package net.glorat.ledger

import java.time.Instant
import java.util.UUID

import org.json4s._

package object json {
  implicit val jsonFormats: Formats = org.json4s.DefaultFormats + UUIDSerializer + InstantSerializer

}

object UUIDSerializer extends CustomSerializer[UUID](format => ({
  case JString(str) => UUID.fromString(str)
}, {
  case value: UUID  => {
    JString(value.toString)
  }
}
))

object InstantSerializer extends CustomSerializer[Instant](format => ({
  case JLong(dbl) => Instant.ofEpochMilli(dbl)
  case JInt(num) => Instant.ofEpochMilli(num.toLong)
}, {
  case value: Instant => {
    JInt(value.toEpochMilli)
  }
}))