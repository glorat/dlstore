package controllers

import akka.actor.ActorSystem
import javax.inject.Inject
import net.glorat.cqrs._
import net.glorat.cqrs.example._
import play.api.Logger

import play.api.data.Forms._
import play.api.data._
import play.api.data.format.Formatter
import play.api.mvc.{MessagesActionBuilder, _}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.concurrent.CustomExecutionContext

object CustomMappings {

  val uuidFormatter = new Formatter[java.util.UUID] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], java.util.UUID] = {
      data.get(key).map { value =>
        try {
          Right(java.util.UUID.fromString(value))
        } catch {
          case e: NoSuchElementException => error(key, value + " is not a valid UUID")
        }
      }.getOrElse(error(key, "No UUID."))
    }

    private def error(key: String, msg: String) = Left(List(new FormError(key, msg)))

    override def unbind(key: String, value: java.util.UUID): Map[String, String] = {
      Map(key -> value.toString())
    }
  }

  def uuid: Mapping[java.util.UUID] = Forms.of[java.util.UUID](uuidFormatter)
}

class Application @Inject()(cc: ControllerComponents)(implicit ec: ExecutionContext)
  extends AbstractController(cc)
    with play.api.i18n.I18nSupport {
  val logger = Logger(this.getClass)
  // No command should take that long to run!
  // TODO: But make this all async!
  implicit val actorTimeout: akka.util.Timeout = 1 second

  val svcs = new controllers.Environment
  val read = svcs.read

  // Forms that wrap commands
  val addForm: Form[CreateInventoryItem] = Form(
    mapping("id" -> ignored(java.util.UUID.randomUUID()), "name" -> text)(CreateInventoryItem.apply)(CreateInventoryItem.unapply))
  val renameForm: Form[RenameInventoryItem] = Form(
    mapping("id" -> CustomMappings.uuid, "name" -> text, "version" -> number)(RenameInventoryItem.apply)(RenameInventoryItem.unapply))
  val userForm: Form[CheckInItemsToInventory] = Form(
    mapping(
      "id" -> CustomMappings.uuid,
      "number" -> number,
      "version" -> number)(CheckInItemsToInventory.apply)(CheckInItemsToInventory.unapply))

  val removeForm: Form[RemoveItemsFromInventory] = Form(
    mapping(
      "id" -> CustomMappings.uuid,
      "number" -> number,
      "version" -> number)(RemoveItemsFromInventory.apply)(RemoveItemsFromInventory.unapply))

  implicit def detailToRename(dto: InventoryItemDetailsDto) = {
    renameForm.fill(RenameInventoryItem(dto.id, dto.name, dto.version))
  }

  def init: Future[Any] = {

    val ret:Future[Unit] = {
      val items = read.getInventoryItems
      if (items.size == 0) {
        // Get some stuff in
        val id = java.util.UUID.randomUUID
        val id1 = java.util.UUID.randomUUID
        val id2 = java.util.UUID.randomUUID


        for {
          a <- svcs.cmds.receive (CreateInventoryItem(id1, "Hello"))
          b <- svcs.cmds.receive (CreateInventoryItem(id2, "World"))
          c <- svcs.cmds.receive (CheckInItemsToInventory(id1, 10, 1))
        } yield c

      } else {
        Future.successful(Nil)
      }
    }
    ret.onComplete(_ => logger.info("Application init completed"))
    ret
  }

  def index = Action {
    logger.info("index requested")
    val items = read.getInventoryItems
    Ok(views.html.index(items))
  }

  def add = Action { implicit request =>
    Ok(views.html.add(addForm))
  }
  def changename = Action { implicit request =>
    Ok(views.html.changename(renameForm))
  }

  def rename(id: String) = Action { implicit request =>

    val id2 = java.util.UUID.fromString(id)
    val item = read.getInventoryItemDetails(id2)
    if (item.isDefined)
      Ok(views.html.changename(item.get))
    else
      NotFound
  }

  def detail(id: String) = Action { implicit request =>
    val id2 = java.util.UUID.fromString(id)
    val item = read.getInventoryItemDetails(id2)
    if (item.isDefined)
      Ok(views.html.details(item.get))
    else
      NotFound
  }

  def doAdd() = Action.async { implicit request =>
    val formcmd = addForm.bindFromRequest.get
    val cmd = formcmd.copy(inventoryItemId = java.util.UUID.randomUUID())
    svcs.cmds.receive(cmd).map(x=>Redirect("/"))

  }

  //case class CheckInForm(number: Int, version: Int)
  def doCheckIn() = Action.async { implicit request =>
    userForm.bindFromRequest.fold(
      formWithErrors => {
        val errs = formWithErrors.errors
        errs.foreach(e => logger.warn(e.message))
        scala.concurrent.Future(Redirect("/"))
      },
      formcmd => {
        val ret = svcs.cmds.receive(formcmd)
        ret.map(x => Redirect("/"))
      })

  }

  def checkin(id: String) = Action {
    val item = read.getInventoryItemDetails(java.util.UUID.fromString(id))
    if (item.isDefined)
      Ok(views.html.checkin(item.get))
    else
      NotFound
  }

  def remove(id: String) = Action {
    val item = read.getInventoryItemDetails(java.util.UUID.fromString(id))
    if (item.isDefined)
      Ok(views.html.remove(item.get))
    else
      NotFound
  }

  def doDeactivate(id: String, version: Int) = Action.async {
    val cmd = DeactivateInventoryItem(java.util.UUID.fromString(id), version)
    svcs.cmds.receive(cmd).map(_ => Redirect("/"))
  }

  def doRemove() = Action.async { implicit request =>

    removeForm.bindFromRequest.fold(
      formWithErrors => {
        val errs = formWithErrors.errors
        errs.foreach(e => logger.warn(e.message))
        scala.concurrent.Future(Redirect("/"))
      },
      formcmd => {
        val ret = svcs.cmds.receive(formcmd)
        ret.map(x => Redirect("/"))
      })

  }
  def doChangeName() = Action.async { implicit request =>
    renameForm.bindFromRequest.fold(
      formWithErrors => {
        val errs = formWithErrors.errors
        errs.foreach(e => logger.warn(e.message))
        scala.concurrent.Future(Redirect("/"))
      },
      formcmd => {

        val ret = svcs.cmds.receive(formcmd)
        ret.map(x => Redirect("/"))
      })

  }

}
