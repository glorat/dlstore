package net.glorat.cqrs

import scala.concurrent.Future

trait EventStreamReceiver {
  def handle(ce: CommitedEvent) : Future[Unit]
}
