package net.glorat.cqrs.test

import cakesolutions.kafka.testkit.KafkaServer
import kafka.server.KafkaConfig
import net.glorat.cqrs._
import net.glorat.cqrs.example._
import net.glorat.ledger._
import org.json4s.DefaultFormats
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class TestCQRS extends AnyFlatSpec with BeforeAndAfterAll {
  val kafkaServer = new KafkaServer(kafkaConfig = KafkaServer.defaultConfig + (KafkaConfig.AdvertisedHostNameProp -> "localhost"))
  val kafkaPort = kafkaServer.kafkaPort

  override def beforeAll = {
    kafkaServer.startup()
  }

  override def afterAll() = {
    kafkaServer.close()
  }



  class MyFixture(topic:String) {
    val id : GUID = java.util.UUID.fromString("9d9814f5-f531-4d80-8722-f61dcc1679b8")

    val bdb = new BullShitDatabase()
    val read = new ReadModelFacade(bdb)
    implicit val ec :ExecutionContext = ExecutionContext.global

    val registry = InventoryItem.registry
    val reads = Seq(new InventoryItemDetailView(bdb), new InventoryListView(bdb))
    //val rep = new KafkaLedger(s"localhost:${kafkaPort}", topic, Some(read.streamToVersion), registry)
    //val bus = new KafkaEventDispatcher(s"localhost:${kafkaPort}", topic, reads)

    val rep = new InMemoryLedger(Some(read.streamToVersion), registry)
    val bus = new InMemoryDispatcher(rep, reads)

    val cmds = new InventoryCommandHandlers(rep)


    val sendCommand: Command => Unit = (cmd => {
      val cmdFuture = cmds.receive(cmd).map(x=>{
        //rep.oneProducer.flush()
        bus.pollEventStream()
      });
      Await.result(cmdFuture, Duration.Inf)
    })
  }

  def example(f : MyFixture) : Unit = {
    import f._


    sendCommand(CreateInventoryItem(id, "test"))
    val detail = read.getInventoryItemDetails(id).get

    assert(InventoryItemDetailsDto(id, "test", 0, 1) == detail)
    val created = rep.getById(id, new InventoryItem)
    assert(created.getRevision == detail.version)
    assert(1 == detail.version)
    sendCommand(CheckInItemsToInventory(id, 10, detail.version))
    sendCommand(CheckInItemsToInventory(id, 20, detail.version + 1))

    val d2 = read.getInventoryItemDetails(id)
    assert(true == d2.isDefined)
    assert(InventoryItemDetailsDto(id, "test", 30, 3) == d2.get)
    // println("Current item" + ReadModelFacade.getInventoryItemDetails(id))


    //val evs = store.advanced.getFrom(0).flatMap(_.events).map(em => em.body.asInstanceOf[DomainEvent])
    //evs.foreach(ev => println(ev))
    //assert(3 == evs.size)
  }

  "Inventory example" should "do the obvious" in {
    example(new MyFixture("one"))
  }



  it should "not allow duplicate or concurrent writes" in {
    val f = new MyFixture("two")
    example(f)
    assertThrows[ConcurrencyException] {
      f.sendCommand(CheckInItemsToInventory(f.id, 10, 2))

    }
  }

  it should "return not found for unsaved repo entities" in {
    val f = new MyFixture("two")
    val ret = f.rep.getByIdOpt(java.util.UUID.randomUUID(), new InventoryItem())
    assert (ret.isEmpty)
  }
}
