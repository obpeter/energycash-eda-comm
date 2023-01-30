package at.energydash
package config

import com.typesafe.config.{Config => AkkaConfig, ConfigFactory}

object Config {

  case class MqttMailConfig(url: String, topic: String, qos: Int, consumerId: String)

  lazy val config = ConfigFactory.load()
  config.checkValid(ConfigFactory.defaultReference)

//  val host: String = config.getString("epmsmail.app.host")
//
//  val port: Int = config.getInt("epmsmail.app.port")
//
//  val smtpHost: String         = config.getString("epmsmail.app.server")
//  val smtpPort: Int            = config.getInt("epmsmail.app.emailServerPort")
//  val smtpUser: String         = config.getString("epmsmail.app.user")
//  val smtpPassword: String     = config.getString("epmsmail.app.password")
//  val apiUserKeys: Seq[String] = config.getString("epmsmail.app.apiKeys").split(",").toSeq

//  private lazy val imapConfig = config.getConfig("epmsmail.mail.imap")
//  lazy val imapHost = imapConfig.getString("server")
//  lazy val imapPort = imapConfig.getInt("port")
//  lazy val imapUser = imapConfig.getString("user")
//  lazy val imapPwd = imapConfig.getString("password")

  lazy val emailPersistInbox = config.getString("epmsmail.mail.inbox")
  lazy val emailDomain = (tenant: String) => config.getString(s"epmsmail.mail.${tenant}.domain")

  def getMqttMailConfig: MqttMailConfig = MqttMailConfig(
    config.getString("epmsmail.mqtt.url"),
    config.getString("epmsmail.mqtt.topic"),
    config.getInt("epmsmail.mqtt.qos"),
    config.getString("epmsmail.mqtt.consumer-id")
  )

  lazy val energyTopic = config.getString("epmsmail.mqtt.topics.energyTopic")
  lazy val cmTopic = config.getString("epmsmail.mqtt.topics.cmTopic")
  lazy val cpTopic = config.getString("epmsmail.mqtt.topics.cpTopic")

  def getMailSessionConfig(tenant: String): AkkaConfig = config.getConfig(s"epmsmail.mail.${tenant}")
}