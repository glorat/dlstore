package net.glorat.cqrs

/**
  * Must be a value object
  * Current implementation extends Product to strongly encourage use of case class in Scala
  */
trait AggregateRootState extends Product {
  /**
    * State class must handle any events with no exception
    * @param e domain event
    * @return modified state
    */
  def handle(e:DomainEvent) : AggregateRootState
}
