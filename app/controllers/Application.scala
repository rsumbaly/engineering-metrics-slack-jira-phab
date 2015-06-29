package controllers

import javax.inject.Inject

import play.api.mvc._

import javax.inject.Singleton

import reporter.Reporter

import scala.concurrent.Future

import scala.concurrent.ExecutionContext

@Singleton
class Application @Inject() (
    reporter: Reporter)
  (implicit ec: ExecutionContext) extends Controller {

  def main(usernames: String, nWeeks: Int) = Action {
    Async {
      val teamUsernames = usernames.trim.split(",").toList
      if (teamUsernames.length <= 1) {
        Future.successful(BadRequest("Should have atleast two team members"))
      } else {
        reporter.generateReport(teamUsernames, nWeeks).map { report =>
          Ok(views.html.main(report, nWeeks))
        }.recover { case e: Throwable =>
          BadRequest(s"Error while retrieving report - ${e.getMessage}")
        }
      }
    }
  }

}