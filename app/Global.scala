import com.google.inject.Guice
import com.typesafe.scalalogging.slf4j.StrictLogging
import play.api.GlobalSettings
import play.api.inject.guice.GuiceInjectorBuilder
import play.utils.Colors
import play.api.Play.current

object Global extends GlobalSettings with StrictLogging {

  override def onStart(application: play.api.Application) {
    logger.info(Colors.green("Starting phabricator metrics"))
  }

  override def onStop(application: play.api.Application) { }
  
}
