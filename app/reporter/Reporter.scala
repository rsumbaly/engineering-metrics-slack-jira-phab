package reporter

import javax.inject.Singleton
import javax.inject.Inject

import org.joda.time.DateTime
import phabricator._

import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * For both open and closed reviews
 */
case class DurationReviewMetadata (
  maxClosedReview: Option[Review],
  maxOpenReview: Option[Review])

/**
 * The intent of the reporter is to return a bunch of statistics.
 *
 * In last N weeks:
 * - %age of diffs sent out to - highest to lowest
 * - %age of diffs reviewed for - highest to lowest
 * - Max time to review a closed diff / review an open diff
 * - Max time to get diff reviewed (closed) / get a review on an open diff
 * - Relative %age of diffs being sent wrt team
 * - Relative %age of diffs being reviewed wrt team
 */
case class Report(
  authorUsername: String,
  sentAllReviewsPerUsername: immutable.Map[String, Int],
  receivedAllReviewsPerUsername: immutable.Map[String, Int],
  durationSentReviewMetadata: DurationReviewMetadata,
  durationReceivedReviewMetadata: DurationReviewMetadata,
  relativeSentAllReviews: Double,
  relativeReceivedAllReviews: Double)

trait Reporter {

  /**
   * Return a report per user name
   * @param teamUsernames List of user names
   * @param lastNWeek Look at tickets created in the last N weeks
   * @return Report per person
   */
  def generateReport(
    teamUsernames: List[String],
    lastNWeek: Int): Future[immutable.Map[String, Report]]
}

@Singleton
class ReporterImpl @Inject() (
  queryEngine: PhabricatorQuery)
  (implicit ec: ExecutionContext) extends Reporter {

  override def generateReport(
    teamUsernames: List[String],
    lastNWeek: Int): Future[immutable.Map[String, Report]] = {

    for {
      // Convert all user names to ids
      usernameToPhids <- queryEngine.getPhidForUsername(teamUsernames)

      phidsToUsername = usernameToPhids.map { case (from, to) => to -> from }.toMap

      // Get all the team phids
      teamPhids = usernameToPhids.values.toList

      // Check if we got everything back properly
      _ = if (usernameToPhids.size != teamUsernames.length) {
        throw new IllegalArgumentException("Couldn't find for some user name "
          + (teamUsernames.toSet -- usernameToPhids.keys))
      }

      // Get all the reviews
      allReviews <- queryEngine.getAllReviewsFromAuthorPhids(teamPhids,
        DateTime.now.minusWeeks(lastNWeek))

    } yield {

      // Let's start the munching of data

      val sentReviewEdge = mutable.Map[(String, String), Int]()
      val durationOpenSentReviewsPerPhid = mutable.Map[String, Review]()
      val durationClosedSentReviewsPerPhid = mutable.Map[String, Review]()
      val durationOpenReceivedReviewsPerPhid = mutable.Map[String, Review]()
      val durationClosedReceivedReviewsPerPhid = mutable.Map[String, Review]()

      // Filter reviews that don't have anyone within the team
      // We still need to filter out people who are not in the team below.
      val filteredReviews = allReviews.filter(_._2.reviewersPhids.intersect(teamPhids).nonEmpty)

      // Start by grouping by author id to get
      // percentSentAllReviewsPerPerson, maxSentReviewMetadata, relativeSentAllReviews
      filteredReviews.groupBy(_._1).foreach { case (authorPhid, reviewsSent) =>

        // Get counts per edge
        reviewsSent.foreach { reviewSent =>

          // First update the edge count
          reviewSent._2.reviewersPhids.foreach { reviewerPhid =>

            // Remove reviewers not in the team
            if ( teamPhids.contains(reviewerPhid)) {
              val edge = (authorPhid, reviewerPhid)
              sentReviewEdge += edge -> (sentReviewEdge.getOrElse(edge, 0) + 1)
            }
          }
        }

        // Filter only open reviews and get max time
        reviewsSent
          .filter(_._2.committedAt.isEmpty)
          .reduceOption { (review1, review2) =>
            if (review1._2.daysDifference > review2._2.daysDifference) review1 else review2 }
          .collect { case (_, review) => durationOpenSentReviewsPerPhid += (authorPhid -> review) }

        // Filter only closed reviews and get max time
        reviewsSent
          .filter(_._2.committedAt.nonEmpty)
          .reduceOption { (review1, review2) =>
            if (review1._2.daysDifference > review2._2.daysDifference) review1 else review2 }
          .collect { case (_, review) => durationClosedSentReviewsPerPhid += (authorPhid -> review) }
      }

      // Similarly, group by the reviewer id to get
      // percentReceivedAllReviewsPerPerson, maxReceivedMetadata, relativeReceivedAllReviews
      filteredReviews.flatMap { case (authorPhid, reviewSent) =>
        reviewSent.reviewersPhids.map { reviewerPhid =>
          (reviewerPhid -> reviewSent)
        }
      }.groupBy(_._1).foreach { case (reviewerPhid, reviewsReceived) =>

        // Filter only open reviews and get max time
        reviewsReceived
          .filter(_._2.committedAt.isEmpty)
          .reduceOption { (review1, review2) =>
            if (review1._2.daysDifference > review2._2.daysDifference) review1 else review2 }
          .collect { case (_, review) => durationOpenReceivedReviewsPerPhid += (reviewerPhid -> review) }

        // Filter only closed reviews and get max time
        reviewsReceived
          .filter(_._2.committedAt.nonEmpty)
          .reduceOption { (review1, review2) =>
            if (review1._2.daysDifference > review2._2.daysDifference) review1 else review2 }
          .collect { case (_, review) => durationClosedReceivedReviewsPerPhid += (reviewerPhid -> review) }
      }

      val totalSent = sentReviewEdge.values.sum

      teamUsernames.map { reportUsername =>

        val reportPhid = usernameToPhids.get(reportUsername).get

        val filteredSentReviewEdge = sentReviewEdge
          .filter { case ((fromPhid, _), _) => fromPhid == reportPhid }
        val filteredReceivedReviewEdge = sentReviewEdge
          .filter { case ((_, toPhid), _) => toPhid == reportPhid }

        val report = Report (reportUsername,
          filteredSentReviewEdge.map { case ((_, toPhid), count) => (phidsToUsername.get(toPhid).get -> count) }.toMap,
          filteredReceivedReviewEdge.map { case ((fromPhid, _), count) => (phidsToUsername.get(fromPhid).get -> count) }.toMap,
          DurationReviewMetadata(
            durationClosedSentReviewsPerPhid.get(reportPhid),
            durationOpenSentReviewsPerPhid.get(reportPhid)),
          DurationReviewMetadata(
            durationClosedReceivedReviewsPerPhid.get(reportPhid),
            durationOpenReceivedReviewsPerPhid.get(reportPhid)),
          BigDecimal(filteredSentReviewEdge.values.sum * 100.0 / totalSent).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble,
          BigDecimal(filteredReceivedReviewEdge.values.sum * 100.0 / totalSent).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble)

        // Return tuple of (user name and report)
        (reportUsername -> report)
      }.toMap

    }
  }
}

object ReporterImpl extends App {

  import ExecutionContext.Implicits.global

  // Only for testing locally
  val testClient = new PhabricatorClientImpl(PhabricatorConfig(
    apiUrl = "<FILL>",
    user = "<FILL>",
    certificate = "<FILL>"))
  val testQuery = new PhabricatorQueryImpl(testClient)
  val testReporter = new ReporterImpl(testQuery)

  println(Await.result(
    testReporter.generateReport(
      List("<FILL>"), 2), 10 minutes))

}
