package at.energydash
package domain.dao

import java.sql.Timestamp

case class EmailOutbox(id: Option[Long], tenant: String, subject: String, content: Array[Byte], received: Timestamp)
