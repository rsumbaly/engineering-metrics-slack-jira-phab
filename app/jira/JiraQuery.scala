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
import net.oauth.OAuth
import net.oauth.OAuthConsumer
import net.oauth.ParameterStyle
import net.oauth.client.OAuthClient
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
      val request2 = jiraClient.accessor.newRequestMessage(null, request.getUri.toString,
        Collections.emptyList())
      val accepted = jiraClient.accessor.consumer.getProperty(OAuthConsumer.ACCEPT_ENCODING)
      if (accepted != null) {
        request2.getHeaders.add(new OAuth.Parameter(HttpMessage.ACCEPT_ENCODING, accepted.toString))
      }
      val ps = jiraClient.accessor.consumer.getProperty(OAuthClient.PARAMETER_STYLE)
      val style = if (ps == null) {
        ParameterStyle.BODY
      } else {
        ParameterStyle.valueOf(ps.toString)
      }
      val httpRequest = HttpMessage.newRequest(request2, style)
      httpRequest.headers.foreach(
        (f: java.util.Map.Entry[String, String]) => request.setHeader(f.getKey, f.getValue))
      request.setUri(httpRequest.url.toURI)
    }

  })
}
