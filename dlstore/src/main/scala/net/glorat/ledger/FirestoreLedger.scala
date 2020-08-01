package net.glorat.ledger

import java.io.IOException

import com.google.cloud.firestore.{DocumentChange, DocumentSnapshot, EventListener, FirestoreException, QueryDocumentSnapshot, QuerySnapshot}
import net.glorat.cqrs.{AggregateRoot, CommittedEvent, DomainEvent, EventStreamReceiver, GUID, RepositoryWithEntityStream}
import org.json4s.Formats
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{ClassTag, Manifest}
import org.json4s._
import org.json4s.jackson.Serialization.{read, write}

class FirestoreLedger(cfg:FirestoreLedgerConfig) (implicit ec: ExecutionContext, implicit val formats: Formats) extends RepositoryWithEntityStream {
  val logger = LoggerFactory.getLogger(getClass)

  import com.google.cloud.firestore.Firestore
  import com.google.firebase.cloud.FirestoreClient

  val db: Firestore = FirestoreClient.getFirestore

  protected def readLinesForId[T<:DomainEvent](id: GUID)(implicit mf: Manifest[T]): Seq[CommittedEvent] = {
    import scala.collection.JavaConverters._

    // asynchronously retrieve all users// asynchronously retrieve all users

    val query = db.collection(cfg.mainCollectionName)
      .document(id.toString)
      .collection(cfg.subCollectionName)
      .orderBy("streamRevision")
      .get()
    // ...
    // query.get() blocks on response
    val querySnapshot = query.get
    val documents = querySnapshot.getDocuments
    documents.asScala.map(document =>
      documentToCommittedEvent[T](document)
    )

  }

  private def documentToCommittedEvent[T <: DomainEvent](document: QueryDocumentSnapshot)(implicit mf: Manifest[T]) = {
    val domainEvent = read[T](document.getString("event"))
    CommittedEvent(
      event = domainEvent,
      streamId = java.util.UUID.fromString(document.getString("streamId")),
      streamRevision = document.getLong("streamRevision").toInt,
      // repoTimestamp = Instant.ofEpochMilli(document.getLong("repoTimestamp"))
    )
  }

  def listen[T<:DomainEvent](id: GUID, registrations: Seq[EventStreamReceiver])(implicit mf: Manifest[T]): Any = {
    val records = db.collection(cfg.mainCollectionName).document(id.toString).collection(cfg.subCollectionName)

    records.addSnapshotListener(new EventListener[QuerySnapshot]() {
      override def onEvent(snapshots: QuerySnapshot, e: FirestoreException): Unit = {
        if (e != null) {
          System.err.println("Listen failed: " + e)
          return
        }

        import scala.collection.JavaConversions._
        for (dc <- snapshots.getDocumentChanges) {
          dc.getType match {
            case DocumentChange.Type.ADDED => {
              // System.out.println("New: " + dc.getDocument.getData)
              try {
                val ce = documentToCommittedEvent[T](dc.getDocument)
                registrations.foreach(_.handle(ce))
              }
              catch {
                case e:Exception => logger.error(e.toString)
              }

            }
            case DocumentChange.Type.MODIFIED =>
              logger.error("Event stream modified!")

            case DocumentChange.Type.REMOVED =>
              logger.error("Event stream element deleted!")

            case _ =>

          }
        }
      }
    })
  }

  override def save(aggregate: AggregateRoot, expectedVersion: Int): Future[Unit] = {
    val evs = aggregate.getUncommittedChanges
    var i = expectedVersion
    val cevs = evs.map(ev => {
      i += 1
      CommittedEvent(ev, aggregate.id, i)
    })

    val records = db.collection(cfg.mainCollectionName).document(aggregate.id.toString).collection(cfg.subCollectionName)

    val futures: Seq[Future[Unit]] = cevs.map(cev => {
      val data = Map[String,AnyRef](
        "streamId" -> cev.streamId.toString,
        "streamRevision" -> Long.box(cev.streamRevision),
        // "repoTimestamp" -> Long.box(cev.repoTimestamp.toEpochMilli),
        "event" -> write(cev.event)
      )
      val javaData: java.util.Map[String, AnyRef] = {
        import scala.collection.JavaConverters._
        data.asJava
      }
      val fut = records.add(javaData)
      // fut.asScala // wait for scala 2.13
      Future[Unit] {
        fut.get
      }
//      Future.successful()
    }).toSeq

    val ret = Future.sequence(futures)
    ret.map(_ => ())
  }

  // TODO: This is to be deprecated because the getOrElse is not safe
  def getById[T <: AggregateRoot](id: GUID, tmpl: T)(implicit evidence$1: ClassTag[T]): T = {
    getByIdOpt(id, tmpl).getOrElse(tmpl)
  }


  def getByIdOpt[T <: AggregateRoot](id: GUID, tmpl: T)(implicit evidence$1: ClassTag[T]): Option[T] = {
    try {
      val cevs = readLinesForId[DomainEvent](id)
      var revision = 1
      cevs.foreach(cev => {
        if (id == cev.streamId) {
          if (revision == cev.streamRevision) {
            tmpl.loadFromHistory(Seq(cev.event), cev.streamRevision)
            revision += 1
          }
          else {
            logger.error(s"${id} has invalid CE at revision ${cev.streamRevision} is ignored")
          }
        }
      })
      Some(tmpl)
    }
    catch {
      case e: IOException => {
        logger.warn(s"getById(${id}) failed because ${e.getMessage}")
        None
      }
    }
  }

  def getAllCommits(id: GUID): Seq[CommittedEvent] = {
    val cevsOrig = readLinesForId[DomainEvent](id)
    var revision = 1
    val cevs = cevsOrig.filter(cev => {
      if (id == cev.streamId) {
        if (revision == cev.streamRevision) {
          revision += 1
          true
        }
        else {
          logger.error(s"${id} has invalid CE at revision ${cev.streamRevision} is ignored")
          false
        }
      }
      else {
        false
      }
    })
    cevs
  }

  // Use for admin only
  override def purge(id: GUID) = {
    val allDocRefs = db.collection(cfg.mainCollectionName).document(id.toString).collection(cfg.subCollectionName).listDocuments()
    import scala.collection.JavaConverters._
    val it = allDocRefs.iterator.asScala
    val futures: Seq[Future[Unit]] = it.map(docRef =>
      Future[Unit] {
        docRef.delete().get
      }
    ).toSeq
    Future.sequence(futures).mapTo[Unit]

  }

}

case class FirestoreLedgerConfig(url: String, mainCollectionName: String, subCollectionName: String)