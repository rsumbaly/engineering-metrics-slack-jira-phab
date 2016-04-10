package jira

import java.io.File
import java.lang.Thread.UncaughtExceptionHandler
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

import com.atlassian.jira.rest.client.api.AuthenticationHandler
import com.atlassian.httpclient.api.Request
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.JiraRestClientFactory
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue
import com.atlassian.jira.rest.client.api.domain.input.FieldInput
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.google.common.collect.ImmutableList
import com.typesafe.scalalogging.slf4j.StrictLogging
import net.oauth.ParameterStyle
import net.oauth.http.HttpMessage
import play.api.Configuration

trait JiraQuery {

  def getOpenTickets(username: String, project: String): List[Issue]

  def getTicket(username: String, ticket: String): Issue

  def createTicket(username: String, project: String, title: String, description: String): Issue

  def closeTicket(username: String, ticket: String)

}

class JiraQueryImpl @Inject()(jiraClient: JiraClient, config: Configuration) extends JiraQuery with StrictLogging {

  import scala.collection.JavaConversions._

  val factory: JiraRestClientFactory = new AsynchronousJiraRestClientFactory

  // Adopted from http://www.azar.in/questions/5314547/oauth-authentication-in-jira-by-using-java
  @volatile var sessionHandler = config.getString("jira.sessionHandler").get
  @volatile var accessToken = config.getString("jira.accessToken").get
  @volatile var restClient: JiraRestClient = createRestClient(accessToken)

  private def createRestClient(inputAccessToken: String): JiraRestClient = {
    factory.create(jiraClient.BASE_URI, new AuthenticationHandler {
      override def configure(request: Request): Unit = {

        jiraClient.accessor.accessToken = inputAccessToken
        val message = jiraClient.accessor.newRequestMessage(request.getMethod.toString, request.getUri.toString,
          Collections.emptyList(), request.getEntityStream)
        val newRequest = HttpMessage.newRequest(message, ParameterStyle.BODY)
        newRequest.headers.foreach(
          (f: java.util.Map.Entry[String, String]) => request.setHeader(f.getKey, f.getValue))
        request.setUri(newRequest.url.toURI)
      }
    })
  }

  // Updates access code every 3 days
  Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
    override def newThread(r: Runnable): Thread = {
      val thread = new Thread(r, "accessTokenRefresher")
      thread.setDaemon(true)
      thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
        override def uncaughtException(t: Thread, e: Throwable): Unit =
          logger.error("Error in access token refresher", e)
      })
      thread
    }
  }).scheduleAtFixedRate(new Runnable() {
    override def run(): Unit = {
      logger.info("Refreshing = " + accessToken + " , " + sessionHandler)
      val replacementAccessToken = jiraClient.refreshAccessToken(accessToken, sessionHandler)
      logger.info(
        "New access token = " + replacementAccessToken.accessToken + " , " + replacementAccessToken.sessionHandler)
      val newRestClient = createRestClient(replacementAccessToken.accessToken)

      accessToken = replacementAccessToken.accessToken
      sessionHandler = replacementAccessToken.sessionHandler
      restClient = newRestClient
    }
  }, 1, 1, TimeUnit.DAYS)

  override def getOpenTickets(username: String, project: String) = {
    restClient.getSearchClient.searchJql(
      "assignee = " + username + " and project = " + project +
        " and status not in (closed, resolved) order by created desc")
      .get.getIssues.toList
  }

  override def createTicket(username: String, project: String, title: String, description: String) = {
    val key = restClient.getIssueClient.createIssue(new IssueInputBuilder()
      .setProjectKey(project)
      .setAssigneeName(username)
      .setSummary(title)
      .setDescription(description)
      .setIssueTypeId(1L)
      .build()).get().getKey
    getTicket(username, key)
  }

  override def getTicket(username: String, ticket: String) = {
    restClient.getIssueClient.getIssue(ticket).get
  }

  override def closeTicket(username: String, ticket: String) {
    val returnedIssue = getTicket(username, ticket)
    val closingTransition = restClient.getIssueClient.getTransitions(returnedIssue).get.iterator().find(
      _.getName.equalsIgnoreCase("Close issue")).getOrElse(
      throw new IllegalArgumentException("Could not find transition for closing"))
    restClient.getIssueClient.transition(returnedIssue, new TransitionInput(closingTransition.getId,
      ImmutableList.of(new FieldInput("resolution", ComplexIssueInputFieldValue.`with`("name", "Done")),
        new FieldInput("assignee", ComplexIssueInputFieldValue.`with`("name", username))))).get
  }
}

object JiraQueryApp extends App {
  val configuration = Configuration.apply(("jira.accessToken" -> "[FILL]"),
    ("jira.sessionHandler" -> "[FILL]"))
  val client = new JiraQueryImpl(
    new JiraClient(
      JiraConfig("[FILL]",
        new File("[FILL]"),
        "[FILL]",
        "[FILL]")),
    configuration)

  client.restClient.close()
}
