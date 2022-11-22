package at.energydash
package domain.mqtt

case class MqttResponseMessage(msgType: String, payload: String)

case class MqttCpMsgResponse(id: String, status: String)

case class MqttEDAEnvelop(msgType: String, messageId: String, conversationId: String, sender: String, receiver: String, payload: String)

case class MqttVDCModel(docNumber: String, docFile: String, docSignaturDate: String)

case class MqttAPExtension(counterPoint: String, partition: Double)

case class MqttAPModel(counterPointGenerator: String, counterPointConsumers: Array[MqttAPExtension], processDate: String)

case class MqttRPModel(counterPointGenerator: String, counterPointConsumer: String, contractPartner: String, verificationDocument: String, docDate: String, partition: String, processDate: String)