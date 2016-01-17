import com.typesafe.scalalogging.slf4j.StrictLogging
import play.api.GlobalSettings
import play.utils.Colors

object Global extends GlobalSettings with StrictLogging {

  override def onStart(app: play.api.Application) {
    logger.info(Colors.green("Starting phabricator metrics"))
  }

  override def onStop(app: play.api.Application): Unit = {
  }

}
