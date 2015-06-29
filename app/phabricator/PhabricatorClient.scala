package phabricator

import javax.inject.Inject
import javax.inject.Singleton

import dispatch._
import org.json4s.DefaultFormats
import scala.util.parsing.json.JSONObject
import org.json4s.JValue
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait PhabricatorClient {

  /** Call a Conduit method with authentication enabled.
    *
    * @param method The Conduit method to call.
    * @param params The parameters to pass to Conduit.
    * @return The parsed response from Conduit
    */
  def call(
    method: String,
    params: Map[String, Any]): Future[JValue]

}

@Singleton
class PhabricatorClientImpl @Inject() (
    config: PhabricatorConfig)
  (implicit ec: ExecutionContext) extends PhabricatorClient {

  import PhabricatorClientImpl._

  implicit val formats = DefaultFormats

  val time = System.currentTimeMillis / 1000

  // Get MD5 of (time + certificate)
  val authSignatureMd5 = java.security.MessageDigest.getInstance("SHA-1")
  authSignatureMd5.update((time.toString + config.certificate).getBytes)

  // Format it correctly
  val authSignature = authSignatureMd5.digest
    .map(x =>"%02x".format(x)).mkString

  // Now try to get session id
  val connectResponse = call(
    "conduit.connect",
    Map(
      "client" -> CLIENT_NAME,
      "clientVersion" -> CLIENT_VERSION,
      "user" -> config.user,
      "authToken" -> time,
      "authSignature" -> authSignature,
      "host" -> config.apiUrl),
    false).apply()

  val sessionKey = (connectResponse \ "result" \ "sessionKey").extract[String]
  val connectionID = (connectResponse \ "result" \ "connectionID").extract[Int]

  override def call(
    method: String,
    params: Map[String, Any]): Future[JValue] =
    call(method, params, true)

  private def call(
    method: String,
    params: Map[String, Any],
    authenticated: Boolean): Future[JValue] = {

    // Convert to json string
    val jsonString = JSONObject(
      if (authenticated) {
        params ++ Map(
          "__conduit__" -> JSONObject(Map(
            "sessionKey" -> sessionKey,
            "connectionID" -> connectionID
          ))
        )
      } else {
        params
      }).toString()

    // Make the request
    val request = url(config.apiUrl) / method << Map(
      "params" -> jsonString
    )

    // Return the response as json value
    Http(request > as.json4s.Json)
  }
}

object PhabricatorClientImpl {
  final val CLIENT_NAME = "phab-metrics"
  final val CLIENT_VERSION = 1
}