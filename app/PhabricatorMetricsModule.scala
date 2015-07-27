import phabricator._
import play.api.Environment
import play.api.inject.Module
import play.api.Configuration
import reporter.Reporter
import reporter.ReporterImpl

class PhabricatorMetricsModule extends Module {

  def bindings(env: Environment, conf: Configuration) = Seq (
    bind[PhabricatorConfig].toInstance {
      val configs = conf.getConfig("phabricator").get
      PhabricatorConfig(
        apiUrl = configs.getString("apiUrl").get,
        user = configs.getString("user").get,
        certificate = configs.getString("certificate").get)
    },
    bind[PhabricatorClient].to[PhabricatorClientImpl],
    bind[PhabricatorQuery].to[PhabricatorQueryImpl],
    bind[Reporter].to[ReporterImpl]
  )
}
