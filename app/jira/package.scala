import java.io.File

package object jira {

  val CALLBACK_URI = "http://consumer/callback"

  case class JiraConfig(consumerKey: String,
                        privateKeyFile: File,
                        baseUrl: String,
                        callback: String = CALLBACK_URI)

  object Commands extends Enumeration {
    val REQUEST_TOKEN = Value("requestToken")
    val ACCESS_TOKEN = Value("accessToken")
    val TEST_GET_REQUEST = Value("testGetRequest")
    val REFRESH_ACCESS_TOKEN = Value("refreshAccessToken")
  }

  case class TokenSecretVerifierHolder(requestToken: String, verifier: String, secret: String)

  case class AccessTokenSessionHandlerHolder(accessToken: String, sessionHandler: String)

}
