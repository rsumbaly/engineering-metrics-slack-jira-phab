package controllers

import javax.inject.Inject
import javax.inject.Singleton

import com.typesafe.scalalogging.slf4j.StrictLogging
import phabricator.PhabricatorReporter
import phabricator.PhabricatorReporterUtils
import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

@Singleton
class Application @Inject()(reporter: PhabricatorReporter)
                           (implicit ec: ExecutionContext) extends Controller with StrictLogging {

  def main(usernames: String, nWeeks: Int) = Action.async {
    val teamUsernames = usernames.trim.split(",").toList
    if (teamUsernames.length <= 1) {
      Future.successful(BadRequest("Should have atleast two team members"))
    } else {
      reporter.generateSummaryReport(teamUsernames, nWeeks).map { report =>
        val (userNames, reviewMatrix, standardizedMatrix) = PhabricatorReporterUtils.convertToReviewMatrix(report)
        Ok(views.html.main(report, nWeeks, userNames, reviewMatrix, standardizedMatrix))
      }.recover { case e: Throwable =>
        logger.error("Error while retrieving report", e)
        BadRequest(s"Error while retrieving report - ${e.getMessage}")
      }
    }
  }

}