package jira

import java.io.File
import java.util.Collections
import javax.inject.Inject

import com.atlassian.jira.rest.client.api.AuthenticationHandler
import com.atlassian.httpclient.api.Request
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.JiraRestClientFactory
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.typesafe.scalalogging.slf4j.StrictLogging
import net.oauth.ParameterStyle
import net.oauth.http.HttpMessage
import play.api.Configuration

trait JiraQuery {
}

class JiraQueryImpl @Inject()(jiraClient: JiraClient, config: Configuration) extends JiraQuery with StrictLogging {

  val accessToken = config.getString("jira.accessToken").get
  val factory: JiraRestClientFactory = new AsynchronousJiraRestClientFactory()

  // Adopted from http://www.azar.in/questions/5314547/oauth-authentication-in-jira-by-using-java
  val restClient: JiraRestClient = factory.create(jiraClient.BASE_URI, new AuthenticationHandler {
    override def configure(request: Request): Unit = {
      import scala.collection.JavaConversions._

      jiraClient.accessor.accessToken = accessToken
      val message = jiraClient.accessor.newRequestMessage(request.getMethod.toString, request.getUri.toString,
        Collections.emptyList(), request.getEntityStream)
      val newRequest = HttpMessage.newRequest(message, ParameterStyle.BODY)
      newRequest.headers.foreach(
        (f: java.util.Map.Entry[String, String]) => request.setHeader(f.getKey, f.getValue))
      request.setUri(newRequest.url.toURI)
    }

  })
}

object JiraQueryApp extends App {
  val configuration = Configuration.apply(("jira.accessToken" -> "[FILL]"))
  val restClient = new JiraQueryImpl(
    new JiraClient(
      JiraConfig("[FILL]",
        new File("[FILL]"),
        "[FILL]")),
    configuration)

  val issue = new IssueInputBuilder().setProjectKey("[FILL]").setSummary("Summary").setDescription(
    "Description").setAssigneeName("[FILL]").setIssueTypeId(1L).build()
  println(restClient.restClient.getIssueClient.createIssue(issue).get)

  restClient.restClient.close()
}
