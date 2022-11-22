package at.energydash
package domain.util

import com.typesafe.config.{ ConfigFactory, ConfigValue }

object Config {

  case class MqttMailConfig(url: String, topic: String, qos: Int, consumerId: String)

  lazy val config = ConfigFactory.load()
  config.checkValid(ConfigFactory.defaultReference)

  val host: String = config.getString("epmsmail.app.host")

  val port: Int = config.getInt("epmsmail.app.port")

  val smtpHost: String         = config.getString("epmsmail.app.server")
  val smtpPort: Int            = config.getInt("epmsmail.app.emailServerPort")
  val smtpUser: String         = config.getString("epmsmail.app.user")
  val smtpPassword: String     = config.getString("epmsmail.app.password")
//  val apiUserKeys: Seq[String] = config.getString("epmsmail.app.apiKeys").split(",").toSeq

  private lazy val imapConfig = config.getConfig("epmsmail.mail.imap")
  lazy val imapHost = imapConfig.getString("server")
  lazy val imapPort = imapConfig.getInt("port")
  lazy val imapUser = imapConfig.getString("user")
  lazy val imapPwd = imapConfig.getString("password")

  def getMqttMailConfig: MqttMailConfig = MqttMailConfig(
    config.getString("epmsmail.mqtt.url"),
    config.getString("epmsmail.mqtt.topic"),
    config.getInt("epmsmail.mqtt.qos"),
    config.getString("epmsmail.mqtt.consumer-id")
  )
}