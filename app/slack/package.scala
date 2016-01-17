package object slack {

  case class SlackTeamConfig(name: String,
                             hookUrl: String,
                             teamUsernames: List[String])

  case class SlackConfig(teamConfigs: List[SlackTeamConfig])

}
