package jira

import java.io.File
import java.net.URI
import javax.inject.Inject

import com.google.common.collect.{ImmutableList, Lists}
import com.typesafe.scalalogging.slf4j.StrictLogging
import net.oauth.client.OAuthClient
import net.oauth.client.httpclient4.HttpClient4
import net.oauth.signature.RSA_SHA1
import net.oauth._

class JiraClient @Inject()(jiraConfig: JiraConfig) extends StrictLogging {

  val privateKey = scala.io.Source.fromFile(jiraConfig.privateKeyFile).mkString

  val SERVLET_BASE_URL: String = "/plugins/servlet"

  def BASE_URI = new URI(jiraConfig.baseUrl)

  def REQUEST_TOKEN_URL = jiraConfig.baseUrl + SERVLET_BASE_URL + "/oauth/request-token"

  def AUTHORIZED_URL = jiraConfig.baseUrl + SERVLET_BASE_URL + "/oauth/authorize"

  def ACCESS_TOKEN_URL = jiraConfig.baseUrl + SERVLET_BASE_URL + "/oauth/access-token"


  lazy val accessor: OAuthAccessor = {
    val serviceProvider = new OAuthServiceProvider(REQUEST_TOKEN_URL, AUTHORIZED_URL, ACCESS_TOKEN_URL)
    val consumer = new OAuthConsumer(jiraConfig.callback, jiraConfig.consumerKey, null, serviceProvider)
    consumer.setProperty(RSA_SHA1.PRIVATE_KEY, privateKey)
    consumer.setProperty(OAuth.OAUTH_SIGNATURE_METHOD, OAuth.RSA_SHA1)
    new OAuthAccessor(consumer)
  }

  def getAuthorizedUrlForToken(token: String) = AUTHORIZED_URL + "?oauth_token=" + token

  def getRequestToken: TokenSecretVerifierHolder = {
    try {
      val oAuthClient = new OAuthClient(new HttpClient4())
      val message = oAuthClient.getRequestTokenResponse(accessor, OAuthMessage.POST,
        ImmutableList.of(new OAuth.Parameter(OAuth.OAUTH_CALLBACK, jiraConfig.callback))
      )

      TokenSecretVerifierHolder(
        token = accessor.requestToken,
        secret = accessor.tokenSecret,
        verifier = message.getParameter(OAuth.OAUTH_VERIFIER))
    }
    catch {
      case e: Throwable => throw new RuntimeException("Failed to obtain request token", e)
    }
  }

  def makeTestRequest(accessToken: String, url: String) = {
    try {
      val client = new OAuthClient(new HttpClient4())
      accessor.accessToken = accessToken
      client.invoke(accessor, url, Lists.newArrayList()).readBodyAsString
    }
    catch {
      case e: Throwable => throw new RuntimeException("Failed to finish test request", e)
    }
  }

  def getAccessToken(requestToken: String, tokenSecret: String, oauthVerifier: String) {
    try {
      val client = new OAuthClient(new HttpClient4())
      accessor.requestToken = requestToken
      accessor.tokenSecret = tokenSecret
      val message = client.getAccessToken(accessor, OAuthMessage.POST,
        ImmutableList.of(new OAuth.Parameter(OAuth.OAUTH_VERIFIER, oauthVerifier)))
      message.getToken
    }
    catch {
      case e: Throwable => throw new RuntimeException("Failed to get access tokens", e)
    }
  }

}

object JiraClientApp extends App {

  if (args.isEmpty || args.length < 5) {
    throw new IllegalArgumentException("No command specified. Use one of " + Commands.values)
  }

  val command = Commands.withName(args(0))
  val consumerKey = args(1)
  val privateKeyFile = args(2)
  val baseUrl = args(3)
  val callbackUrl = args(4)

  val client = new JiraClient(JiraConfig(consumerKey, new File(privateKeyFile), baseUrl, callbackUrl))
  command match {
    case Commands.REQUEST_TOKEN => {
      if (args.length != 5) {
        sys.error(
          "Should have atleast 5 arguments - requestToken [consumerKey] [privateKeyFile] [baseUrl] [callbackUrl]")
      }

      val token = client.getRequestToken
      println("Request Token is " + token.token)
      println("Request Token secret is " + token.secret)
      println("Authorized url " + client.getAuthorizedUrlForToken(token.token))
    }
    case Commands.ACCESS_TOKEN => {
      if (args.length != 8) {
        sys.error(
          "Should have atleast 8 arguments - accessToken [consumerKey] [privateKeyFile] [baseUrl] [callbackUrl]" +
            " [requestToken] [tokenSecret] [verifier]")
      }

      val requestToken = args(5)
      val tokenSecret = args(6)
      val verifier = args(7)
      println("Access token is " + client.getAccessToken(requestToken, tokenSecret, verifier))
    }
    case Commands.TEST_REQUEST => {
      if (args.length != 7) {
        sys.error(
          "Should have atleast 8 arguments - accessToken [consumerKey] [privateKeyFile] [baseUrl] [callbackUrl]" +
            " [accessToken] [jiraUrl]")
      }

      val accessToken = args(5)
      val jiraUrl = args(6)
      println(jiraUrl)
      println("Test response is " + client.makeTestRequest(accessToken, jiraUrl))
    }
    case _ => throw new IllegalArgumentException("Could not find match")
  }

}
