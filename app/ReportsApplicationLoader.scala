import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.StrictLogging
import play.api.{Mode, Configuration, ApplicationLoader}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}

class ReportsApplicationLoader extends GuiceApplicationLoader with StrictLogging {

  /**
   * Append configuration based on environment
   */
  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    val envConfig = ConfigFactory.parseResourcesAnySyntax(context.environment.classLoader,
      context.environment.mode match {
        case Mode.Prod => "prod.conf"
        case Mode.Dev | Mode.Test => "dev.conf"
      })
    initialBuilder
      .in(context.environment)
      .loadConfig(context.initialConfiguration ++ Configuration(envConfig))
      .overrides(overrides(context): _*)
  }
}
