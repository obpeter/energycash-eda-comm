package at.energydash
package domain.mqtt

//import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
//import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat, enrichAny}
//
////trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
////  implicit val meterEnergyValueFormat = jsonFormat2(MeterEnergyValue)
////  implicit val meterEnergyModelFormat = jsonFormat7(MeterEnergyModel)
////  implicit val mqttPublishFormat = jsonFormat2(MqttPublishCommand)
////  implicit val responseDataFormat = jsonFormat2(ResponseData)
////  implicit val apCounterPointsFormat = jsonFormat3(APCounterPoints)
////  implicit val apConfirmedModelFormat = jsonFormat7(APConfirmedModel)
////  implicit val rpConfirmedModelFormat = jsonFormat5(RPConfirmedModel)
////
////
////  implicit object AnimalJsonFormat extends RootJsonFormat[EdaType] {
////    def write(a: EdaType): JsValue = a match {
////      case p: MeterEnergyModel => p.toJson
////      case c: APConfirmedModel => c.toJson
////      case rp: RPConfirmedModel => rp.toJson
////    }
////
////    def read(value: JsValue): EdaType =
////    // If you need to read, you will need something in the
////    // JSON that will tell you which subclass to use
////      value.asJsObject.fields("kind") match {
////        case JsString("person") => value.convertTo[MeterEnergyModel]
////      }
////  }
////}
//
//object ResponseJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
//
//  implicit val mqttEDAEnvelop: RootJsonFormat[MqttEDAEnvelop] = jsonFormat6(MqttEDAEnvelop)
//  implicit val mqttResponseMessage: RootJsonFormat[MqttResponseMessage] = jsonFormat2(MqttResponseMessage)
//  implicit val mqttCpMsgResponse: RootJsonFormat[MqttCpMsgResponse] = jsonFormat2(MqttCpMsgResponse)
//
//}
//
//object MqttEdaJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
//  implicit val mqttAPExtensionModel: RootJsonFormat[MqttAPExtension] = jsonFormat2(MqttAPExtension)
//  implicit val mqttVDCModel: RootJsonFormat[MqttVDCModel] = jsonFormat3(MqttVDCModel)
//  implicit val mqttAPModel: RootJsonFormat[MqttAPModel] = jsonFormat3(MqttAPModel)
//  implicit val mqttRPModel: RootJsonFormat[MqttRPModel] = jsonFormat7(MqttRPModel)
//
//}