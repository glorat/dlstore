package net.glorat.cqrs

import scala.concurrent.Future

trait CommandHandler {
  // Could have improved modelling here beyond Unit, since Command
  // failures aren't really Exception-al
  def receive: PartialFunction[Command, Future[Unit]]
}
