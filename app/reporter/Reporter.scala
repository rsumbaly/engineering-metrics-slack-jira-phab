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
case class MaxDurationReviewMetadata (
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
 * - All open reviews received
 * - Relative %age of diffs being sent wrt team
 * - Relative %age of diffs being reviewed wrt team
 */
case class SummaryReport(
  authorUsername: String,
  sentAllReviewsPerUsername: immutable.Map[String, Int],
  receivedAllReviewsPerUsername: immutable.Map[String, Int],
  durationSentReviewMetadata: MaxDurationReviewMetadata,
  durationReceivedReviewMetadata: MaxDurationReviewMetadata,
  relativeSentAllReviews: Double,
  relativeReceivedAllReviews: Double)

/**
 * Over all time: 
 * - All open reviews received
 */
case class OpenReviewsReport(
  authorUsername: String,
  openReviews: immutable.Seq[Review])

trait Reporter {

  /**
   * Return a report per user name
   * @param teamUsernames List of user names
   * @param lastNWeek Look at tickets created in the last N weeks
   * @return Report per person
   */
  def generateSummaryReport(
    teamUsernames: List[String],
    lastNWeek: Int): Future[immutable.Map[String, SummaryReport]]
  
  def generateOpenReviewReport(
    teamUsernames: List[String]): Future[immutable.Map[String, OpenReviewsReport]]
}

@Singleton
class ReporterImpl @Inject() (
  queryEngine: PhabricatorQuery)
  (implicit ec: ExecutionContext) extends Reporter {

  override def generateOpenReviewReport (teamUsernames: List[String]) = {
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
        statusList = List(DiffStatus.NEEDS_REVIEW))

    } yield {

      val openReceivedReviewPerPhid = mutable.Map[String, List[Review]]()

      // Filter reviews that don't have anyone within the team
      // We still need to filter out people who are not in the team below.
      val filteredReviews = allReviews.filter(_._2.reviewersPhids.intersect(teamPhids).nonEmpty)

      // Group by the reviewer id
      filteredReviews.flatMap { case (authorPhid, reviewSent) =>
        reviewSent.reviewersPhids.map { reviewerPhid =>
          (reviewerPhid -> reviewSent)
        }
      }.groupBy(_._1).foreach { case (reviewerPhid, reviewsReceived) =>

        // Filter only the open reviews and store them
        openReceivedReviewPerPhid += reviewerPhid -> reviewsReceived
          .filter(_._2.committedAt.isEmpty).map(_._2)

      }

      teamUsernames.map { reportUsername =>
        val reportPhid = usernameToPhids.get(reportUsername).get
        (reportUsername -> OpenReviewsReport(reportUsername,
          openReceivedReviewPerPhid.getOrElse(reportPhid, immutable.Seq.empty[Review])))
      }.toMap
    }
  }

  override def generateSummaryReport(
    teamUsernames: List[String],
    lastNWeek: Int): Future[immutable.Map[String, SummaryReport]] = {

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

        val report = SummaryReport (reportUsername,
          filteredSentReviewEdge.map { case ((_, toPhid), count) => (phidsToUsername.get(toPhid).get -> count) }.toMap,
          filteredReceivedReviewEdge.map { case ((fromPhid, _), count) => (phidsToUsername.get(fromPhid).get -> count) }.toMap,
          MaxDurationReviewMetadata(
            durationClosedSentReviewsPerPhid.get(reportPhid),
            durationOpenSentReviewsPerPhid.get(reportPhid)),
          MaxDurationReviewMetadata(
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
    testReporter.generateSummaryReport(
      List("<FILL>"), 2), 10 minutes))

}
