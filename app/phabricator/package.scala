import org.joda.time.DateTime
import org.joda.time.Duration

import scala.collection.immutable

package object phabricator {

  case class Review(
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

  object DiffStatus extends Enumeration {
    val NEEDS_REVIEW = Value("0")
    val NEEDS_REVISION = Value("1")
    val ACCEPTED = Value("2")
    val CLOSED = Value("3")
    val ABANDONED = Value("4")

    val lookup = DiffStatus.values.map(e => e.toString -> e).toMap

    def find(e: String) = lookup.get(e)

    val ALLOWED_LIST = List(NEEDS_REVIEW, ACCEPTED, CLOSED)
  }

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

  case class PhabricatorConfig(apiUrl: String,
                               user: String,
                               certificate: String) {
    require(apiUrl.endsWith("/api"), s"Api url ${apiUrl} should end with api")
  }

  /**
   * Over all time:
   * - All open reviews received
   */
  case class OpenReviewsReport(
                                authorUsername: String,
                                openReviews: immutable.Seq[Review])

  /**
   * For both open and closed reviews
   */
  case class MaxDurationReviewMetadata(
                                        maxClosedReview: Option[Review],
                                        maxOpenReview: Option[Review])

}
