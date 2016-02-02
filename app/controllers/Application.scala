package controllers

import javax.inject.Inject
import javax.inject.Singleton

import com.typesafe.scalalogging.slf4j.StrictLogging
import phabricator.PhabricatorReporter
import phabricator.PhabricatorReporterUtils
import play.api.libs.json.Json
import play.api.mvc._
import slack.SlackCommandInterpreter
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success
import scala.util.Try

@Singleton
class Application @Inject()(reporter: PhabricatorReporter,
                            commandInterpreter: SlackCommandInterpreter)
                           (implicit ec: ExecutionContext) extends Controller with StrictLogging {

  def phab(usernames: String, nWeeks: Int) = Action.async {
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

  def slack = Action.async { request =>
    Future {
      request.body.asFormUrlEncoded.map { values =>

        val channelName = values.get("channel_name").get(0)
        val text = values.get("text").get(0)
        val token = values.get("token").get(0)

        logger.info("Command - " + channelName + "," + text + "," + token)
        commandInterpreter.executeCommand(channelName, text, token) match {
          case Success(slackMessage) => {
            Try {
              Json.parse(slackMessage.prepare.toString)
            } match {
              case Success(json) => Ok(json)
              case Failure(error) => {
                logger.error("Error while parsing - " + slackMessage, error)
                Ok(
                  Json.parse(SlackCommandInterpreter.ERROR_SLACK_RESPONSE("Error thrown while parsing ",
                    error).prepare.toString))
              }
            }
          }
          case Failure(error) => {
            logger.error("Error in command - " + channelName + ", " + text + ", " + token, error)
            Ok(Json.parse(SlackCommandInterpreter.ERROR_SLACK_RESPONSE("Error thrown in command ",
              error).prepare.toString))
          }
        }

      }.getOrElse {
        BadRequest("Bad state")
      }
    }
  }

}