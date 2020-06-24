package net.glorat.cqrs

import scala.concurrent.Future

trait EventStreamReceiver {
  def handle(ce: CommittedEvent) : Future[Unit]
}
