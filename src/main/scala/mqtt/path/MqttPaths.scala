package at.energydash
package mqtt.path

trait MqttPaths {
  protected def edaPath(tenant: String) = tenant.toLowerCase()

  protected def edaProtocolModulePath(tenant: String, protocolId: String) =
    s"${edaPath(tenant)}/protocol/${protocolId.toLowerCase()}"

  protected def edaReqResPath(tenant: String, protocolId: String) =
    s"eda/response/${edaProtocolModulePath(tenant, protocolId)}"
}
