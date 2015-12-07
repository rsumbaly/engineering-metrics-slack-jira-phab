import javax.inject.Singleton

import akka.actor.{ActorSystem, Props}
import com.google.inject.{Injector, Provides, AbstractModule}
import phabricator._
import play.api.Environment
import play.api.Configuration
import play.api.libs.concurrent.AkkaGuiceSupport
import reporter.Reporter
import reporter.ReporterImpl
import slack._

class PhabricatorMetricsModule(env: Environment, config: Configuration)
  extends AbstractModule with AkkaGuiceSupport {

  override def configure() = {
    import scala.collection.JavaConversions._

    bind(classOf[SlackConfig]).toInstance {
      val configs = config.getConfigList("slack.teams").get
      SlackConfig(configs.map { teamConfig =>
        SlackTeamConfig(teamConfig.getString("name").get,
          teamConfig.getString("hook").get,
          teamConfig.getStringList("users").get.toList)
      }.toList)
    }
    bind(classOf[PhabricatorConfig]).toInstance {
      val configs = config.getConfig("phabricator").get
      PhabricatorConfig(
        apiUrl = configs.getString("apiUrl").get,
        user = configs.getString("user").get,
        certificate = configs.getString("certificate").get)
    }

    bind(classOf[PhabricatorClient]).to(classOf[PhabricatorClientImpl])
    bind(classOf[PhabricatorQuery]).to(classOf[PhabricatorQueryImpl])
    bind(classOf[Reporter]).to(classOf[ReporterImpl])
  }

  @Provides
  @Singleton
  def slackActorProps(injector: Injector): SlackActorProps = {
    new SlackActorProps(Props(injector.getInstance(classOf[SlackActor])))
  }

  @Provides
  @Singleton
  def slackActorRef(actorSystem: ActorSystem, props: SlackActorProps): SlackActorRef = {
    new SlackActorRef(actorSystem.actorOf(props.actorProps, name = "slackActorRef"))
  }

}
