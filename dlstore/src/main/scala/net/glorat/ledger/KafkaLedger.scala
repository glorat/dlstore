package net.glorat.ledger

import cakesolutions.kafka.KafkaProducerRecord.Destination
import cakesolutions.kafka.{KafkaProducer, KafkaProducerRecord, KafkaSerializer}
import net.glorat.cqrs._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  *
  * @param servers
  * @param topic
  * @param streamToRevision - An immediately available eventually consistent map for optimistic lookup. It's just an optimisation
  */
class KafkaLedger(servers:String, topic:String, streamToRevision:Option[GUID=>Int], registry:DomainEvent=>AggregateRoot)(implicit ec:ExecutionContext) extends Repository with Logging {
  val entityView = new EntityView(registry)
  val dispatch = new KafkaEventDispatcher(servers, topic, Seq(entityView))

  override def save(aggregate: AggregateRoot, expectedVersion: Int): Future[Unit] = {
    if (streamToRevision.isDefined) {
      val latestVersion = streamToRevision.get(aggregate.id)
      if (expectedVersion < latestVersion) {
        // Someone saved already
        throw new ConcurrencyException(s"Trying to save aggregate from version ${expectedVersion} when ${latestVersion} already in DB")
      }
    }


    val evs = aggregate.getUncommittedChanges
    var i=expectedVersion
    val cevs = evs.map(ev => {
      i+=1
      CommitedEvent(ev, aggregate.id, i)
    })

    log.debug(s"Persisting ${cevs.size} events to Kafka...")
    val futs = cevs.map(cev => oneProducer.send(KafkaProducerRecord(Destination(topic,0), Some("key"), cev)))
    val oneFut = Future.sequence(futs).map(x => ()) // Throw away RecordMetaData
    oneProducer.flush()

    dispatch.pollEventStream()
    // TODO: Check if we have arrived, rather than rely on flush?
  }

  override def getById[T <: AggregateRoot : ClassTag](id: GUID, tmpl: T): T = {
    entityView.getById(id, tmpl)
  }


  private val stringSerializer = (msg: String) => msg.getBytes
  lazy val oneProducer = KafkaProducer(producerConfig)

  def producerConfig: KafkaProducer.Conf[String, Object] = {
    KafkaProducer.Conf(KafkaSerializer(stringSerializer),
      KafkaSerializer(BinarySerializer.serializer),
      bootstrapServers = servers)
  }
}
