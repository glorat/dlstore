package net.glorat.cqrs.test

import net.glorat.cqrs.{AggregateRoot, Command, CommittedEvent, DomainEvent, GUID, RepositoryWithEntityStream}
import net.glorat.cqrs.example.{BullShitDatabase, CheckInItemsToInventory, CreateInventoryItem, InventoryCommandHandlers, InventoryItem, InventoryItemCreated, InventoryItemDetailView, InventoryItemDetailsDto, InventoryItemRenamed, InventoryListView}
import net.glorat.ledger.{ConcurrencyException, FirestoreLedger, FirestoreLedgerConfig, InMemoryLedger, InstantSerializer, UUIDSerializer}
import org.json4s.{DefaultFormats, ShortTypeHints}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Ignore, Tag}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

object UsesGoogleEnv extends Tag(if (System.getenv("GOOGLE_APPLICATION_CREDENTIALS")!=null) "" else classOf[Ignore].getName)



class TestFirestore extends FlatSpec with BeforeAndAfterAll {
  implicit val ec :ExecutionContext = ExecutionContext.global
  val hints = ShortTypeHints(InventoryItem.allEventTypes)
  implicit val formats = DefaultFormats.withHints(hints) + UUIDSerializer + InstantSerializer
  val id : GUID = java.util.UUID.fromString("9d9814f5-f531-4d80-8722-f61dcc1679b8")
  val firestoreConfig = FirestoreLedgerConfig("https://gainstrack-poc.firebaseio.com", "test_users", "test_records")

  val bdb = new BullShitDatabase()
  val reads = Seq(new InventoryItemDetailView(bdb), new InventoryListView(bdb))

  val rep = if (System.getenv("GOOGLE_APPLICATION_CREDENTIALS")!=null) {
    val ret = new FirestoreLedger(firestoreConfig)
    ret.listen[DomainEvent](id, reads)
    ret
  } else {
    // Won't get used
    new RepositoryWithEntityStream() {
      override def getAllCommits(id: GUID): Seq[CommittedEvent] = ???
      override def save(aggregate: AggregateRoot, expectedVersion: Int): Future[Unit] = ???
      override def getById[T <: AggregateRoot : ClassTag](id: GUID, tmpl: T): T = ???
    }
  }


  override def beforeAll = {
    rep.purge(id)
    bdb.purge()
  }

  class FirestoreFixture(topic: String) {

    val cmds = new InventoryCommandHandlers(rep)

    val sendCommand: Command => Unit = (cmd => {
      val cmdFuture = cmds.receive(cmd)
      Await.result(cmdFuture, Duration.Inf)
    })
  }


  def example(f : FirestoreFixture) : Unit = {
    import f._


    sendCommand(CreateInventoryItem(id, "test"))
    //val detail = read.getInventoryItemDetails(id).get

    // assert(InventoryItemDetailsDto(id, "test", 0, 1) == detail)
    val created = rep.getById(id, new InventoryItem)
    //assert(created.getRevision == detail.version)
    //assert(1 == detail.version)
    sendCommand(CheckInItemsToInventory(id, 10, 1))
    sendCommand(CheckInItemsToInventory(id, 20, 2))

    //val evs = store.advanced.getFrom(0).flatMap(_.events).map(em => em.body.asInstanceOf[DomainEvent])
    //evs.foreach(ev => println(ev))
    //assert(3 == evs.size)
  }

  "Inventory example" should "do the obvious" taggedAs UsesGoogleEnv in {
    example(new FirestoreFixture("one"))
  }



  ignore should "not allow duplicate or concurrent writes" taggedAs UsesGoogleEnv in {
    val f = new FirestoreFixture("two")
    example(f)
    assertThrows[ConcurrencyException] {
      f.sendCommand(CheckInItemsToInventory(id, 10, 2))

    }
  }
}
