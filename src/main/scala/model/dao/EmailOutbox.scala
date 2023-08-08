package at.energydash
package model.dao

import java.sql.Timestamp

case class EmailOutbox(id: Option[Long], tenant: String, subject: String, content: Array[Byte], received: Timestamp)
