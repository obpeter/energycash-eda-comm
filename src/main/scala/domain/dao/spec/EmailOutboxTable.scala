package at.energydash
package domain.dao.spec

import domain.dao.model.EmailOutbox

import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.SqlType

import java.sql.{Blob, Date, Timestamp}

trait EmailOutboxTable { this: Db =>

  import config.profile.api._

  class EmailOutboxs(tag: Tag) extends Table[EmailOutbox](tag, Some("eda"), "inbox") {
    def id: Rep[Long] = column[Long]("id", SqlType("SERIAL"),  O.PrimaryKey,  O.AutoInc)
    def tenant: Rep[String] = column[String]("tenant")
    def content: Rep[Array[Byte]] = column[Array[Byte]]("content")
    def received: Rep[Timestamp] = column[Timestamp]("received")
    def * : ProvenShape[EmailOutbox] = (id.?, tenant, content, received) <> (EmailOutbox.tupled, EmailOutbox.unapply)
  }

  val emailOutboxs = TableQuery[EmailOutboxs]
}