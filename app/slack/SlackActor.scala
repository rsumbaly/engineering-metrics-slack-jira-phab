package slack

import javax.inject.Inject

import akka.actor.{ActorRef, Props, Actor}
import net.gpedro.integrations.slack.{SlackAttachment, SlackMessage, SlackApi}
import org.joda.time.DateTime
import org.joda.time.Duration
import reporter.Reporter
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

case class SlackActorProps(actorProps: Props)
case class SlackActorRef(actorRef: ActorRef)

case object SlackTick

class SlackActor @Inject() (
    config: SlackConfig,
    reporter: Reporter,
    executionContext: ExecutionContext) extends Actor {

  implicit val ec = executionContext
  lazy val connectorPerTeam = config.teamConfigs.map { teamConfig =>
    (teamConfig.name -> new SlackApi(teamConfig.hookUrl))
  }.toMap

  lazy val teamNamesPerTeam = config.teamConfigs.map { teamConfig =>
    (teamConfig.name -> teamConfig.teamUsernames)
  }.toMap

  override def receive: Receive = {
    case SlackTick => {
      connectorPerTeam.foreach { team =>

        val message = new SlackMessage("Open reviews (12 hour report)")
        val result = Await.result(reporter.generateOpenReviewReport(teamNamesPerTeam(team._1)), 1 minute)
        result.foreach { case (user, report) =>
          if ( report.openReviews.size > 0 ) {
            message.addAttachments(new SlackAttachment()
              .setAuthorName(user)
              .setTitle(report.openReviews.size + " reviews open")
              .setFallback(report.openReviews.size + " reviews open for " + user)
              .setColor( if (report.openReviews.size > 2)
                  "danger"
                else
                  "warning")
              .setText(report.openReviews.foldLeft(new StringBuilder)( (sb, review) =>
                sb.append("\n" + review.title + " [" + review.reviewUrl + "] (Open for " +
                  new Duration(review.createdAt.getMillis, DateTime.now.getMillis).getStandardDays + " days)")).toString))
          } else {
            message.addAttachments(new SlackAttachment()
              .setAuthorName(user)
              .setTitle("Good job - no open reviews!")
              .setFallback("Good job - no open reviews!")
              .setColor("good"))
          }
        }
        team._2.call(message)
      }
    }
  }

}


