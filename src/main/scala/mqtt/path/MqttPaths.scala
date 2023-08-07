package at.energydash
package mqtt.path

trait MqttPaths {
  protected def edaPath(tenant: String) =
    tenant.toLowerCase()

  protected def edaProtocolModulePath(tenant: String, protocolId: String) =
    s"${edaPath(tenant)}/protocol/${protocolId.toLowerCase()}"

  protected def edaStateModulePath(cameraId: String, moduleId: String) =
    s"${edaPath(cameraId)}/state/$moduleId"
}
