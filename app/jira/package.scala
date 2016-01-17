import java.io.File

package object jira {

  case class JiraConfig(consumerKey: String,
                        privateKeyFile: File,
                        val baseUrl: String,
                        callback: String)

  object Commands extends Enumeration {
    val REQUEST_TOKEN = Value("requestToken")
    val ACCESS_TOKEN = Value("accessToken")
  }

  case class TokenSecretVerifierHolder(token: String, verifier: String, secret: String)

}
