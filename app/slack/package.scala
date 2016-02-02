import com.google.gson.JsonObject
import net.gpedro.integrations.slack.SlackAttachment
import net.gpedro.integrations.slack.SlackMessage

package object slack {

  case class SlackTeamConfig(channelName: String,
                             hookUrl: String,
                             teamUsernames: List[String])

  case class SlackConfig(teamConfigs: List[SlackTeamConfig],
                         commandToken: String)

  class ResponseBasedSlackMessage(message: String, isInChannel: Boolean = false)
    extends SlackMessage(message) {

    import scala.collection.JavaConversions._

    override def prepare(): JsonObject = {
      val base = super.prepare()
      base.addProperty("response_type", if (isInChannel)
        "in_channel"
      else
        "ephemeral")
      base
    }

    def setAttachments(attachments: List[SlackAttachment]) = {
      super.setAttachments(attachments)
      this
    }
  }

}

