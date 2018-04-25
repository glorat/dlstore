package net.glorat.cqrs

import scala.concurrent.Future
import scala.reflect.ClassTag

trait Repository {
  def save(aggregate: AggregateRoot, expectedVersion: Int) : Future[Unit]
  def getById[T <: AggregateRoot: ClassTag](id: GUID, tmpl: T): T
}
