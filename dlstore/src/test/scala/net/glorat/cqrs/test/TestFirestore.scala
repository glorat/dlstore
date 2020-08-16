package net.glorat.cqrs.test

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import net.glorat.cqrs.{AggregateRoot, Command, CommittedEvent, DomainEvent, GUID, RepositoryWithEntityStream}
import net.glorat.cqrs.example.{BullShitDatabase, CheckInItemsToInventory, CreateInventoryItem, InventoryCommandHandlers, InventoryItem, InventoryItemCreated, InventoryItemDetailView, InventoryItemDetailsDto, InventoryItemRenamed, InventoryListView, ReadModelFacade}
import net.glorat.ledger.{ConcurrencyException, FirestoreLedger, FirestoreLedgerConfig, InMemoryLedger, InstantSerializer, UUIDSerializer}
import org.json4s.{DefaultFormats, ShortTypeHints}
import org.scalatest.{Ignore, Tag}
import org.scalatest.flatspec.AnyFlatSpec

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

object UsesGoogleEnv extends Tag(if (System.getenv("GOOGLE_APPLICATION_CREDENTIALS")!=null) "" else classOf[Ignore].getName)



class TestFirestore extends AnyFlatSpec with org.scalatest.BeforeAndAfterEach {
  implicit val ec :ExecutionContext = ExecutionContext.global
  val hints = ShortTypeHints(InventoryItem.allEventTypes)
  implicit val formats = DefaultFormats.withHints(hints) + UUIDSerializer + InstantSerializer
  val id : GUID = java.util.UUID.fromString("9d9814f5-f531-4d80-8722-f61dcc1679b8")
  val firestoreConfig = FirestoreLedgerConfig("https://gainstrack-poc.firebaseio.com", "test_users", "test_records")

  val bdb = new BullShitDatabase()
  val read = new ReadModelFacade(bdb)
  val reads = Seq(new InventoryItemDetailView(bdb), new InventoryListView(bdb))

//  System.setProperty("https.proxyHost", "localhost")
//  System.setProperty("https.proxyPort", "8003")
//  System.setProperty("com.google.api.client.should_use_proxy", "true")

  val rep = if (System.getenv("GOOGLE_APPLICATION_CREDENTIALS")!=null) {
    val options: FirebaseOptions =
      new FirebaseOptions.Builder()
        // .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .setDatabaseUrl(firestoreConfig.url)
        .build

    FirebaseApp.initializeApp(options)
    
    val ret = new FirestoreLedger(firestoreConfig)
    ret.listen[DomainEvent](id, reads)
    ret
  } else {
    // Won't get used
    new RepositoryWithEntityStream() {
      override def getAllCommits(id: GUID): Seq[CommittedEvent] = ???
      override def save(aggregate: AggregateRoot, expectedVersion: Int): Future[Unit] = ???
      override def getByIdOpt[T <: AggregateRoot : ClassTag](id: GUID, tmpl: T): Option[T] = None
      override def purge(id: GUID): Unit = {}
    }
  }

  override def beforeEach: Unit = {
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
    assert(rep.getAllCommits(id).length == 1)
    val detail = read.getInventoryItemDetails(id).get

    assert(InventoryItemDetailsDto(id, "test", 0, 1) == detail)

    val created = rep.getById(id, new InventoryItem)
    assert(created.getRevision == detail.version)
    assert(1 == detail.version)
    sendCommand(CheckInItemsToInventory(id, 10, 1))
    sendCommand(CheckInItemsToInventory(id, 20, 2))

    val checkedin = rep.getById(id, new InventoryItem)
    assert(rep.getAllCommits(id).length == 3)

  }

  "Inventory example" should "do the obvious" taggedAs UsesGoogleEnv in {
    example(new FirestoreFixture("one"))
  }



  // Test pessimistic concurrency checking behaviour
  it should "ignore duplicate or concurrent writes" taggedAs UsesGoogleEnv in {
    val f = new FirestoreFixture("two")
    example(f)
    assert(rep.getAllCommits(id).length == 3)
    f.sendCommand(CheckInItemsToInventory(id, 10, 2))
    assert(rep.getAllCommits(id).length == 3)
  }

  it should "return not found for unsaved repo entities" taggedAs UsesGoogleEnv in {
    val ret = rep.getByIdOpt(java.util.UUID.randomUUID(), new InventoryItem())
    assert (ret.isEmpty)
  }
}
