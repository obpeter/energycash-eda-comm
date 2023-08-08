package at.energydash
package mqtt.path

trait MqttPaths {
  protected def edaPath(tenant: String) = tenant.toLowerCase()

  protected def edaProtocolModulePath(tenant: String, protocolId: String) =
    s"${edaPath(tenant)}/protocol/${protocolId.toLowerCase()}"

  protected def edaStateModulePath(tenant: String, moduleId: String) =
    s"${edaPath(tenant)}/state/$moduleId"
}
