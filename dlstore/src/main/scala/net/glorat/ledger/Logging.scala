package net.glorat.ledger

trait Logging {
  lazy val log = org.slf4j.LoggerFactory.getLogger(this.getClass)
}
