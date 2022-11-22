package at.energydash
package domain.mqtt

import akka.Done
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSubscriptions}
import akka.stream.alpakka.mqtt.scaladsl.MqttSource
import akka.stream.scaladsl.Source
import at.energydash.domain.util.Config
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.Future

object MqttEmail {

  val consumerSettings =
    MqttConnectionSettings(
        Config.getMqttMailConfig.url,
        Config.getMqttMailConfig.consumerId,
        new MemoryPersistence)
      .withAutomaticReconnect(true)

  def mqttSource: Source[MqttMessage, Future[Done]] = MqttSource.atMostOnce(
    consumerSettings,
    MqttSubscriptions(Map(Config.getMqttMailConfig.topic -> MqttQoS.AtLeastOnce)),
    bufferSize = 20
  )
}

