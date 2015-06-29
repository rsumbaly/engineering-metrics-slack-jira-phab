import javax.inject.Singleton

import com.google.inject.Provides
import phabricator._
import com.tzavellas.sse.guice.ScalaModule
import play.api.Configuration
import play.api.Application
import reporter.Reporter
import reporter.ReporterImpl
import scala.concurrent.ExecutionContext

class PhabricatorMetricsModule(app: Application) extends ScalaModule {

  protected def configure() {
    bind[PhabricatorClient].to[PhabricatorClientImpl]
    bind[PhabricatorQuery].to[PhabricatorQueryImpl]
    bind[Reporter].to[ReporterImpl]
  }

  @Provides
  def playExecutionContext(): ExecutionContext =
    play.api.libs.concurrent.Execution.defaultContext

  @Provides
  @Singleton
  def currentConfiguration(): Configuration = app.configuration

  @Provides
  @Singleton
  private[this] def phabricatorConfig(configuration: Configuration): PhabricatorConfig = {
    val configs = configuration.getConfig("phabricator").get
    PhabricatorConfig(
      apiUrl = configs.getString("apiUrl").get,
      user = configs.getString("user").get,
      certificate = configs.getString("certificate").get)
  }

}
