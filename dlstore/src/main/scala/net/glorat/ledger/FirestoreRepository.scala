package net.glorat.ledger

import java.io.IOException
import java.time.Instant
import java.util.UUID

import net.glorat.cqrs._
import org.json4s
import org.json4s.JsonAST.JLong
import org.json4s.{CustomSerializer, Formats, JInt, JString}
import org.json4s.jackson.Serialization.{read, write}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{ClassTag, Manifest}

/**
 * Environment variable GOOGLE_APPLICATION_CREDENTIALS needs setting for this to work
 * @see https://firebase.google.com/docs/admin/setup
 * @param databaseUrl
 * @param collectionName
 * @param subCollectionName
 * @param ec
 */
class FirestoreRepository(databaseUrl:String, collectionName:String, subCollectionName:String="records")(implicit ec: ExecutionContext) extends RepositoryWithEntityStream {
  val logger = LoggerFactory.getLogger(getClass)

  import com.google.auth.oauth2.GoogleCredentials
  import com.google.cloud.firestore.Firestore
  import com.google.firebase.{FirebaseApp, FirebaseOptions}
  import com.google.firebase.cloud.FirestoreClient
  //val serviceAccount = new FileInputStream("path/to/serviceAccountKey.json")

  import com.google.api.client.http.HttpTransport
  import com.google.api.client.http.javanet.NetHttpTransport
  import com.google.auth.http.HttpTransportFactory
  import java.net.InetSocketAddress
  import java.net.Proxy

  val address = new InetSocketAddress("localhost", 8003)
  val transport: HttpTransport = new NetHttpTransport.Builder().setProxy(new Proxy(Proxy.Type.HTTP, address)).build
  val transportFactory: HttpTransportFactory = new HttpTransportFactory() {
    override def create: HttpTransport = transport
  }

  val options: FirebaseOptions =
    new FirebaseOptions.Builder()
      // .setCredentials(GoogleCredentials.fromStream(serviceAccount))
      .setCredentials(GoogleCredentials.getApplicationDefault(transportFactory))
      .setDatabaseUrl(databaseUrl)
      .setHttpTransport(transport)
      .build

  FirebaseApp.initializeApp(options)

  val db: Firestore = FirestoreClient.getFirestore


  protected implicit val jsonFormats: Formats = org.json4s.DefaultFormats + UUIDSerializer + InstantSerializer

  protected def readLinesForId[T<:DomainEvent](id: GUID)(implicit mf: Manifest[T]): Seq[CommittedEvent] = {
    import scala.collection.JavaConverters._

    // asynchronously retrieve all users// asynchronously retrieve all users

    val query = db.collection(collectionName).document(id.toString).collection(subCollectionName).get()
    // ...
    // query.get() blocks on response
    val querySnapshot = query.get
    val documents = querySnapshot.getDocuments
    documents.asScala.map(document =>
      CommittedEvent(
        event = read[T](document.getString("event")),
        streamId = java.util.UUID.fromString(document.getString("streamId")),
        streamRevision = document.getLong("streamRevision").toInt,
        // repoTimestamp = Instant.ofEpochMilli(document.getLong("repoTimestamp"))
      )
    )

  }

  override def save(aggregate: AggregateRoot, expectedVersion: Int): Future[Unit] = {
    import scala.collection.JavaConverters._

    val evs = aggregate.getUncommittedChanges
    var i = expectedVersion
    val cevs = evs.map(ev => {
      i += 1
      CommittedEvent(ev, aggregate.id, i)
    })

    val records = db.collection(collectionName).document(aggregate.id.toString).collection(subCollectionName)

    val futures: Seq[Future[Unit]] = cevs.map(cev => {
      val data = Map[String,AnyRef](
        "streamId" -> cev.streamId,
        "streamRevision" -> Long.box(cev.streamRevision),
        // "repoTimestamp" -> Long.box(cev.repoTimestamp.toEpochMilli),
        "event" -> write(cev.event)
      )
      val javaData: java.util.Map[String, AnyRef] = data.asJava
      val fut = records.add(javaData)
      // fut.asScala // wait for scala 2.13
      Future[Unit] {
        fut.get
      }
    }).toSeq

    val ret = Future.sequence(futures).mapTo[Unit]
    ret
  }

  // TODO: This is to be deprecated because the getOrElse is not safe
  def getById[T <: AggregateRoot](id: GUID, tmpl: T)(implicit evidence$1: ClassTag[T]): T = {
    getByIdOpt(id, tmpl).getOrElse(tmpl)
  }


  def getByIdOpt[T <: AggregateRoot](id: GUID, tmpl: T)(implicit evidence$1: ClassTag[T]): Option[T] = {
    try {
      val cevs = readLinesForId(id)
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
    val cevsOrig = readLinesForId(id)
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
    val allDocRefs = db.collection("users").document(id.toString).collection("records").listDocuments()
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

object UUIDSerializer extends CustomSerializer[UUID](format => ({
  case JString(str) => UUID.fromString(str)
}, {
  case value: UUID  => {
    JString(value.toString)
  }
}
))

object InstantSerializer extends CustomSerializer[Instant](format => ({
  case JLong(dbl) => Instant.ofEpochMilli(dbl)
  case JInt(num) => Instant.ofEpochMilli(num.toLong)
}, {
  case value: Instant => {
    JInt(value.toEpochMilli)
  }
}))