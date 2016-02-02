
import java.io.File

import com.google.inject.AbstractModule
import jira.JiraClient
import jira.JiraConfig
import jira.JiraQuery
import jira.JiraQueryImpl
import org.quartz.SchedulerFactory
import org.quartz.impl.StdSchedulerFactory
import phabricator._
import play.api.Environment
import play.api.Configuration
import slack._

class ReportsMetricsModule(env: Environment, config: Configuration)
  extends AbstractModule {

  override def configure() = {
    import scala.collection.JavaConversions._

    // Jira specific configurations
    bind(classOf[JiraConfig]).toInstance {
      val configs = config.getConfig("jira").get
      JiraConfig(configs.getString("consumerKey").get,
        new File(configs.getString("privateKeyFile").get),
        configs.getString("baseUrl").get,
        configs.getString("callback").get)
    }
    bind(classOf[JiraClient])
    bind(classOf[JiraQuery]).to(classOf[JiraQueryImpl]).asEagerSingleton()

    // Phabricator specific configuration
    bind(classOf[PhabricatorConfig]).toInstance {
      val configs = config.getConfig("phabricator").get
      PhabricatorConfig(
        apiUrl = configs.getString("apiUrl").get,
        user = configs.getString("user").get,
        certificate = configs.getString("certificate").get)
    }

    bind(classOf[PhabricatorClient]).to(classOf[PhabricatorClientImpl])
    bind(classOf[PhabricatorQuery]).to(classOf[PhabricatorQueryImpl])
    bind(classOf[PhabricatorReporter]).to(classOf[PhabricatorReporterImpl])

    // Slack specific configuration
    bind(classOf[SlackConfig]).toInstance {
      val slackConfig = config.getConfig("slack").get
      val configs = slackConfig.getConfigList("teams").get
      SlackConfig(configs.map { teamConfig =>
        SlackTeamConfig(teamConfig.getString("channelName").get,
          teamConfig.getString("hookUrl").get,
          teamConfig.getStringList("users").get.toList)
      }.toList,
        slackConfig.getString("commandToken").get)
    }
    bind(classOf[SlackJob])
    bind(classOf[GuiceJobFactory])
    bind(classOf[SchedulerFactory]).toInstance(new StdSchedulerFactory("quartz.properties"))
    bind(classOf[SlackScheduler]).asEagerSingleton

    bind(classOf[SlackCommandInterpreter]).to(classOf[SlackCommandInterpreterImpl])

  }


}
