package at.energydash
package domain.dao.model

import java.sql.{Blob, Date, Timestamp}

case class EmailOutbox(id: Option[Long], tenant: String, content: Array[Byte], received: Timestamp)
