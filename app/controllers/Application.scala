package controllers

import javax.inject.Inject
import javax.inject.Singleton

import akka.actor.ActorSystem
import com.typesafe.scalalogging.slf4j.StrictLogging
import play.api.mvc._
import play.libs.Akka
import reporter._
import slack.{SlackActorRef, SlackTick}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class Application @Inject() (
    reporter: Reporter,
    system: ActorSystem,
    slackActorRef: SlackActorRef)
  (implicit ec: ExecutionContext) extends Controller with StrictLogging {

  Akka.system.scheduler.schedule(1 seconds, 12 hours, slackActorRef.actorRef, SlackTick)

  def main(usernames: String, nWeeks: Int) = Action.async {
    val teamUsernames = usernames.trim.split(",").toList
    if (teamUsernames.length <= 1) {
      Future.successful(BadRequest("Should have atleast two team members"))
    } else {
      reporter.generateSummaryReport(teamUsernames, nWeeks).map { report =>
        val (userNames, reviewMatrix, standardizedMatrix) = ReporterUtils.convertToReviewMatrix(report)
        Ok(views.html.main(report, nWeeks, userNames, reviewMatrix, standardizedMatrix))
      }.recover { case e: Throwable =>
        logger.error("Error while retrieving report", e)
        BadRequest(s"Error while retrieving report - ${e.getMessage}")
      }
    }
  }

}