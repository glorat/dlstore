package net.glorat.ledger

import net.glorat.cqrs._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class InMemoryDispatcher(store:RepositoryWithSingleStream, var registrations: Seq[EventStreamReceiver])(implicit val ec:ExecutionContext)
extends Logging{
  private var pos = -1

  def pollEventStream(): Future[Unit] = {


    log.debug("InMemoryDispatcher polling for more events")
    val snap = store.allCommittedEvents
    if (snap.size > pos) {
      val cms = snap.slice(pos+1, snap.size+1)
      log.debug("InMemoryDispatcher acquired {} more events", cms.size)
      val ret = cms.map(c => handle(c))
      Future.sequence(ret).map(x => ())
    }
    else {
      log.debug("InMemoryDispatcher had no more events")
      Future.successful()
    }
  }

  def handle(ce: CommittedEvent): Future[Unit] = {
    // Publish to registrations
    // These might be done in parallel!
    val all = registrations.map(_.handle(ce))
    Future.sequence(all).map(x => ())
  }
}

class InMemoryLedger(streamToRevision:Option[GUID=>Int], registry:DomainEvent=>AggregateRoot)(implicit val ec:ExecutionContext) extends RepositoryWithSingleStream with Logging {
  var committedEvents: List[CommittedEvent] = List()
  val entityView = new EntityView(registry)

  def allCommittedEvents = committedEvents

  override def save(aggregate: AggregateRoot, expectedVersion: Int): Future[Unit] = {
    if (streamToRevision.isDefined) {
      val latestVersion = streamToRevision.get(aggregate.id)
      if (expectedVersion < latestVersion) {
        // Someone saved already
        throw new ConcurrencyException(s"Trying to save aggregate from version ${expectedVersion} when ${latestVersion} already in DB")
      }
    }

    val evs = aggregate.getUncommittedChanges
    var i = expectedVersion
    val cevs = evs.map(ev => {
      i += 1
      CommittedEvent(ev, aggregate.id, i)
    })

    committedEvents ++= cevs

    val foo = cevs.map(e => entityView.handle(e))
    Future.sequence(foo).map(_ => ())
  }

  override def getByIdOpt[T <: AggregateRoot : ClassTag](id: GUID, tmpl: T): Option[T] = {
    entityView.getByIdOpt(id, tmpl)
  }

}
