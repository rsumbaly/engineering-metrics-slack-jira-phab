package slack

import javax.inject.Inject

import com.atlassian.jira.rest.client.api.domain.Issue
import com.google.common.collect.ImmutableList
import jira.JiraConfig
import jira.JiraQuery
import net.gpedro.integrations.slack.SlackAttachment
import net.gpedro.integrations.slack.SlackField
import phabricator.PhabricatorReporter
import phabricator.Review

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

trait SlackCommandInterpreter {
  def executeCommand(channelName: String, command: String, token: String): Try[ResponseBasedSlackMessage]
}

object SlackCommandInterpreter {


  val HELP_TEXT = "/jumbo review [phab-username] \n" +
    "/jumbo ticket [jira-username] [project] \n" +
    "/jumbo ticket [jira-username] [ticket-no] \n" +
    "/jumbo ticket [jira-username] [ticket-no] close \n" +
    "/jumbo ticket [jira-username] create [project] [title] [description] \n"

  def ERROR_SLACK_RESPONSE(message: String, error: Throwable) =
    new ResponseBasedSlackMessage(message + ". Exception: " + error.toString)

  def HELP_SLACK_RESPONSE(message: String = "") =
    new ResponseBasedSlackMessage(message + " \n" + HELP_TEXT)

  def ATTACHMENT_SLACK_RESPONSE(response: String, attachment: List[SlackAttachment], isInChannel: Boolean = false) =
    new ResponseBasedSlackMessage(response, isInChannel).setAttachments(attachment)
}

class SlackCommandInterpreterImpl @Inject()(jiraConfig: JiraConfig,
                                            slackConfig: SlackConfig,
                                            phabClient: PhabricatorReporter,
                                            jiraQuery: JiraQuery)
  extends SlackCommandInterpreter {

  import SlackCommandInterpreter._

  private def convertReviewToAttachment(review: Review): SlackAttachment = {
    new SlackAttachment()
      .setTitle(review.title)
      .setTitleLink(review.reviewUrl)
      .setFallback(review.title)
      .setFields(ImmutableList.of(new SlackField()
        .setTitle("Days old").setValue(review.daysDifference + " days")))
  }

  def runPhabricatorCommands(commandTokens: List[String]): ResponseBasedSlackMessage = {
    if (commandTokens.length != 2) {
      return HELP_SLACK_RESPONSE("Missing user name for phabricator")
    }
    val username = commandTokens(1)
    val report = Await.result(phabClient.generateOpenReviewUserName(username, 10), 10 seconds)
    val attachments = report.openReviews.map(convertReviewToAttachment(_)).toList
    return ATTACHMENT_SLACK_RESPONSE("Phabricator report for " + username, attachments)
  }

  private def convertIssueToAttachment(issue: Issue): SlackAttachment = {
    new SlackAttachment()
      .setTitle(issue.getSummary)
      .setText(issue.getDescription)
      .setTitleLink(jiraConfig.baseUrl + "/browse/" + issue.getKey)
      .setFallback(issue.getSummary)
      .setFields(ImmutableList.of(new SlackField()
        .setTitle("Created on").setValue(issue.getCreationDate.toString("MM/dd/yyyy")),
        new SlackField()
          .setTitle("Status").setValue(issue.getStatus.getName),
        new SlackField()
          .setTitle("Creator").setValue(issue.getReporter.getDisplayName),
        new SlackField()
          .setTitle("Key").setValue(issue.getKey)))
  }

  def runJiraCommands(channelName: String, commandTokens: List[String]): ResponseBasedSlackMessage = {

    if (commandTokens.length < 3) {
      return HELP_SLACK_RESPONSE("Bad jira command [need min 3 args] - " + commandTokens)
    }
    val username = commandTokens(1)
    commandTokens.length match {
      case 3 => {
        val projectOrTicket = commandTokens(2)
        if (projectOrTicket.contains("-")) {
          // It is a ticket (Not an ideal check)
          return ATTACHMENT_SLACK_RESPONSE("Issue " + projectOrTicket,
            List(convertIssueToAttachment(jiraQuery.getTicket(username, projectOrTicket))))
        } else {
          return ATTACHMENT_SLACK_RESPONSE("Issues for " + username,
            jiraQuery.getOpenTickets(username, projectOrTicket)
              .map(convertIssueToAttachment(_)))
        }
      }
      case 4 => {
        val ticket = commandTokens(2)
        val state = commandTokens(3)
        if (!(state.equalsIgnoreCase("resolve") || state.equalsIgnoreCase("close"))) {
          return HELP_SLACK_RESPONSE("Bad jira command [resolve | close] " + commandTokens)
        }
        jiraQuery.closeTicket(username, ticket)
        return ATTACHMENT_SLACK_RESPONSE("Resolve issue " + ticket,
          List(convertIssueToAttachment(jiraQuery.getTicket(username, ticket))), true)
      }
      case 6 => {
        val state = commandTokens(2)
        if (!(state.equalsIgnoreCase("create"))) {
          return HELP_SLACK_RESPONSE("Bad jira command [create] - " + commandTokens)
        }
        val project = commandTokens(3)
        val title = commandTokens(4)
        val description = commandTokens(5)
        return ATTACHMENT_SLACK_RESPONSE("Created issue ",
          List(convertIssueToAttachment(jiraQuery.createTicket(username, project, title, description))), true)
      }
      case _ => return HELP_SLACK_RESPONSE("Bad jira command [no match] - " + commandTokens)
    }
  }

  override def executeCommand(channelName: String, command: String, token: String): Try[ResponseBasedSlackMessage] = {

    if (!token.equals(slackConfig.commandToken)) {
      return Try(HELP_SLACK_RESPONSE("Tokens did not match - " + token))
    }

    val commandTokens = command.trim.split("[ ]+(?=([^\"]*\"[^\"]*\")*[^\"]*$)")
      .filterNot(_.trim.length == 0).toList

    if (commandTokens.length < 1) {
      return Try(HELP_SLACK_RESPONSE("Should have minimum 1 arguments - " + commandTokens))
    }

    Try {
      commandTokens(0) match {
        case "help" => HELP_SLACK_RESPONSE("Help")
        case "review" => runPhabricatorCommands(commandTokens)
        case "ticket" => runJiraCommands(channelName, commandTokens)
        case _ => HELP_SLACK_RESPONSE("Couldn't find match")
      }
    }
  }
}
