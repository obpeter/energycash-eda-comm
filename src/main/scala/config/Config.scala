package at.energydash
package config

import com.typesafe.config.{Config => AkkaConfig, ConfigFactory}

object Config {
  import scala.jdk.CollectionConverters._

  case class MqttMailConfig(url: String, topic: String, qos: Int, consumerId: String)

//  lazy val config = ConfigFactory.load("application-test.conf")
  lazy val config = ConfigFactory.load()
  config.checkValid(ConfigFactory.defaultReference)

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

  def getTenants: List[String] = config.getList("epmsmail.app.tenants").unwrapped().asScala.map(e => e.toString).toList
}