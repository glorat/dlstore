package controllers

import net.glorat.cqrs._
import cakesolutions.kafka.testkit.KafkaServer
import net.glorat.cqrs.example._
import net.glorat.ledger.{KafkaEventDispatcher, KafkaLedger}

import scala.concurrent.{ExecutionContext, Future}

class Environment()(implicit val ec:ExecutionContext) {
  val kafka = new KafkaServer()
  kafka.startup()
  val kafkaPort = kafka.kafkaPort



  // default Actor constructor
  //val cmdActor = new InventoryCommandActor(cmds)
  val bdb = new BullShitDatabase
  val viewActor = new InventoryItemDetailView(bdb)

  val repo = new KafkaLedger(s"localhost:${kafkaPort}", "inventory", None, InventoryItem.registry)
  val bus = new KafkaEventDispatcher(s"localhost:${kafkaPort}", "inventory", Seq(viewActor, new InventoryListView(bdb)))

  val inner = new InventoryCommandHandlers(repo)

  class CmdHandler extends InventoryCommandHandlers(repo) {
    override def receive: PartialFunction[Command, Future[Unit]] = {
      val x = super.receive
      x.andThen {
        x => bus.pollEventStream()
      }
    }
  }
  val cmds = new CmdHandler
  val read = new ReadModelFacade(bdb)

}
