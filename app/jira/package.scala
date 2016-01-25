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
  }

  case class TokenSecretVerifierHolder(token: String, verifier: String, secret: String)

}
