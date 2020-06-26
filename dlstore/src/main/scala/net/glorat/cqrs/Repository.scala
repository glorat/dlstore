package net.glorat.cqrs

import scala.concurrent.Future
import scala.reflect.ClassTag

trait Repository {
  def save(aggregate: AggregateRoot, expectedVersion: Int) : Future[Unit]
  def getById[T <: AggregateRoot: ClassTag](id: GUID, tmpl: T): T
}

trait RepositoryWithSingleStream extends Repository {
  def allCommittedEvents: Seq[CommittedEvent]
}

trait RepositoryWithEntityStream extends Repository {
  def purge(id: GUID): Unit = ???
  def getAllCommits(id: GUID) : Seq[CommittedEvent]
}