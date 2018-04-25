import play.api._

object Global extends GlobalSettings {



  override def onStart(app: Application) {
    Logger.info("Global application has started")
  }

  override def onStop(app: Application) {
    Logger.info("Global application shutdown...")
  }

}
