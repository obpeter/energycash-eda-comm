package at.energydash
package mqtt.path



trait MqttPaths {

  private def normalizeProtocolId(id: String): String = {
    id.toLowerCase match {
      case "cr_msg_03.03" => "cr_msg"
      case _ => id.toLowerCase
    }
  }

  protected def edaPath(tenant: String) = tenant.toLowerCase()

  protected def edaProtocolModulePath(tenant: String, protocolId: String) =
    s"${edaPath(tenant)}/protocol/${normalizeProtocolId(protocolId)}"

  protected def edaReqResPath(tenant: String, protocolId: String) =
    s"eda/response/${edaProtocolModulePath(tenant, protocolId)}"
}
