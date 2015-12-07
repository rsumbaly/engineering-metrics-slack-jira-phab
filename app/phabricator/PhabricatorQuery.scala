package phabricator

import javax.inject.Inject
import javax.inject.Singleton

import org.joda.time.DateTime
import org.joda.time.Duration
import org.json4s.JsonAST._

import scala.util.parsing.json.JSONArray
import scala.concurrent.Future
import scala.concurrent.ExecutionContext


object DiffStatus extends Enumeration {
  val NEEDS_REVIEW = Value("0")
  val NEEDS_REVISION = Value("1")
  val ACCEPTED = Value("2")
  val CLOSED = Value("3")
  val ABANDONED = Value ("4")

  val lookup = DiffStatus.values.map(e => e.toString -> e).toMap
  def find(e: String) = lookup.get(e)

  val ALLOWED_LIST = List(NEEDS_REVIEW, ACCEPTED, CLOSED)
}

case class Review (
  reviewUrl: String,
  authorPhid: String,
  reviewersPhids: List[String],
  title: String,
  createdAt: DateTime,
  committedAt: Option[DateTime]) {

  // TODO: Only count weekdays
  val daysDifference =
    new Duration(createdAt, committedAt.getOrElse(DateTime.now)).getStandardDays
}

trait PhabricatorQuery {

  def getPhidForUsername(usernames: List[String]): Future[Map[String, String]]

  /**
   * Migrate to Guava multimap (because Scala one is useless)
   */
  def getAllReviewsFromAuthorPhids(
    authorPhid: List[String],
    createdFrom: DateTime = DateTime.now.withYear(2000),
    statusList: List[DiffStatus.Value] = DiffStatus.ALLOWED_LIST): Future[List[(String, Review)]]
}

@Singleton
class PhabricatorQueryImpl @Inject() (
  client: PhabricatorClient)
  (implicit ec: ExecutionContext) extends PhabricatorQuery {

  override def getPhidForUsername(
    usernames: List[String]): Future[Map[String, String]] = {
    for {
      response <- client.call(
        "user.query",
        Map("usernames" -> JSONArray(usernames)))
    } yield {
      val results: List[(String, String)] = for {
        JObject(o) <- (response \ "result");
        JField("userName", JString(userName)) <- o;
        JField("phid", JString(phid)) <- o
      } yield {
        (userName -> phid)
      }
      results.toMap
    }
  }

  override def getAllReviewsFromAuthorPhids(
    authorPhids: List[String],
    createdFrom: DateTime ,
    statusList: List[DiffStatus.Value]): Future[List[(String, Review)]] = {
    for {
      response <- client.call(
        "differential.query",
        Map("order" -> "order-created",
          "authors" -> JSONArray(authorPhids)))
    } yield {
      val result: List[(String, Review)] = for {
        JObject(o) <- (response \ "result")

        // Get all the fields
        JField("uri", JString(uri)) <- o
        JField("authorPHID", JString(authorPhid)) <- o
        JField("reviewers", JArray(reviewersList)) <- o
        JField("dateCreated", JString(createdAtString)) <- o
        JField("dateModified", JString(modifiedAtString)) <- o
        JField("status", JString(statusString)) <- o
        JField("title", JString(title)) <- o

        // Convert them as necessary
        createdAt = new DateTime(createdAtString.toLong * 1000)
        modifiedAt = new DateTime(modifiedAtString.toLong * 1000)
        reviewers = reviewersList.map(_.values.toString)
        status = DiffStatus.find(statusString)

        if status.isDefined &&
          statusList.contains(status.get) &&
          createdAt.isAfter(createdFrom)

      } yield {

        val review = status.get match {
          case DiffStatus.NEEDS_REVIEW =>
            Review(uri, authorPhid, reviewers, title, createdAt, None)
          case DiffStatus.ACCEPTED | DiffStatus.CLOSED =>
            Review(uri, authorPhid, reviewers, title, createdAt, Some(modifiedAt))
          case _ =>
            throw new IllegalArgumentException("This state should never happen as we filter")
        }
        (authorPhid -> review)
      }
      result
    }
  }
}
