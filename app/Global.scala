import com.google.inject.Guice
import com.typesafe.scalalogging.slf4j.StrictLogging
import play.api.GlobalSettings
import play.utils.Colors
import play.api.Play.current

object Global extends GlobalSettings with StrictLogging {

  /**
   * Bind types such that whenever TextGenerator is required, an instance of WelcomeTextGenerator will be used.
   */
  lazy val injector = Guice.createInjector(new PhabricatorMetricsModule(current))

  /**
   * Controllers must be resolved through the application context. There is a special method of GlobalSettings
   * that we can override to resolve a given controller. This resolution is required by the Play router.
   */
  override def getControllerInstance[A](controllerClass: Class[A]): A = injector.getInstance(controllerClass)

  override def onStart(application: play.api.Application) {
    logger.info(Colors.green("Starting phabricator metrics"))
  }

  override def onStop(application: play.api.Application) { }
  
}
