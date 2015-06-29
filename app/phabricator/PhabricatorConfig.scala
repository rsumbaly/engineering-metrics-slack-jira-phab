package phabricator

case class PhabricatorConfig(
  apiUrl: String,
  user: String,
  certificate: String) {
  require(apiUrl.endsWith("/api"), s"Api url ${apiUrl} should end with api")
}


