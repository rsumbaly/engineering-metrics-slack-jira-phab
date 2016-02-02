package slack

import javax.inject.Inject

import net.gpedro.integrations.slack.{SlackAttachment, SlackMessage, SlackApi}
import org.joda.time.DateTime
import org.joda.time.Duration
import org.quartz.{JobExecutionContext, Job}
import phabricator.PhabricatorReporter
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

class SlackJob @Inject()(config: SlackConfig,
                         reporter: PhabricatorReporter,
                         executionContext: ExecutionContext) extends Job {

  implicit val ec = executionContext
  lazy val connectorPerTeam = config.teamConfigs.map { teamConfig =>
    (teamConfig.channelName -> new SlackApi(teamConfig.hookUrl))
  }.toMap

  lazy val teamNamesPerTeam = config.teamConfigs.map { teamConfig =>
    (teamConfig.channelName -> teamConfig.teamUsernames)
  }.toMap

  override def execute(context: JobExecutionContext) = {
    connectorPerTeam.foreach { team =>

      val message = new SlackMessage("Open reviews (24 hour report)")
      val result = Await.result(reporter.generateOpenReviewReport(teamNamesPerTeam(team._1)), 1 minute)

      if (result.size == 0 || result.values.flatten(_.openReviews).size == 0) {
        message.setText("No open reviews today! Yay!")
      } else {
        result.foreach { case (user, report) =>
          if (report.openReviews.size > 0) {
            message.addAttachments(new SlackAttachment()
              .setAuthorName(user)
              .setTitle(report.openReviews.size + " reviews open")
              .setFallback(report.openReviews.size + " reviews open for " + user)
              .setColor(if (report.openReviews.size > 2)
                "danger"
              else
                "warning")
              .setText(report.openReviews.foldLeft(new StringBuilder)((sb, review) =>
                sb.append("\n" + review.title + " [" + review.reviewUrl + "] (Open for " +
                  new Duration(review.createdAt.getMillis,
                    DateTime.now.getMillis).getStandardDays + " days)")).toString))
          } else {
            message.addAttachments(new SlackAttachment()
              .setAuthorName(user)
              .setTitle("Good job - no open reviews!")
              .setFallback("Good job - no open reviews!")
              .setColor("good"))
          }
        }
      }
      team._2.call(message)
    }
  }

}


