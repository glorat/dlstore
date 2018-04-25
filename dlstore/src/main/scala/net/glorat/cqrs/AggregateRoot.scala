package net.glorat.cqrs

import net.glorat.ledger.Logging

abstract class AggregateRoot extends Logging {

  protected var state : AggregateRootState
  private var changes = List[DomainEvent]()
  private var revision: Int = 0

  def id: GUID
  def getState: AggregateRootState

  private def uncommittedChanges = changes

  def getUncommittedChanges = uncommittedChanges.toIterable
  def getRevision = revision

  def applyChange(e: DomainEvent, isNew: Boolean = true) = {
    state = state.handle(e)
    if (isNew) changes = changes :+ e
  }

  /*private[this]*/ def loadFromHistory(history: Traversable[DomainEvent], newRevision: Int) {
    for (event <- history) {
      applyChange(event, false)
    }
    revision = newRevision
  }

  protected def loadState(savedState:AggregateRootState) : Unit = {
    state = savedState
  }

  /*private[this]*/ def loadFromMemento(state: AggregateRootState, streamId: GUID, streamRevision: Int) = {
    loadState(state)
    // require this.id == streamId // after setting State?
    changes = Nil
    revision = streamRevision
  }
}
