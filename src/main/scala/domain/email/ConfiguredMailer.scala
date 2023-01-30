package at.energydash
package domain.email

import com.typesafe.config.Config
import courier.Mailer
import at.energydash.config.ConfigExtensions

import java.util
import java.util.Properties
import javax.mail.{PasswordAuthentication, Session}

object ConfiguredMailer {

  def getSession(config: Config): Session = {
    //First convert the config to a java.util.Properties
    import scala.jdk.CollectionConverters._

    val properties = new Properties()

//    val map: Map[String, Object] = config.getConfig("javaxmail").entrySet().asScala.map({ entry =>
//      entry.getKey -> entry.getValue.unwrapped()
//    }).view.to(Map[String, Object])
    val map = config.getConfig("javaxmail").entrySet().asScala.map(e => e.getKey -> e.getValue.unwrapped()).toMap

    properties.putAll(map.asJava)

    def authenticatorFromConfig(config: Config) = {
      new javax.mail.Authenticator() {
        override def getPasswordAuthentication() = new PasswordAuthentication(config.getString("username"), config.getString("password"))
      }
    }

    val configAuthenticator = config.getOptionConfigured("authenticator", authenticatorFromConfig)

    //Then make the session
    configAuthenticator.fold(Session.getInstance(properties))(
      authenticator => Session.getInstance(properties, authenticator))
  }

  def createMailerFromConfig(config: Config): Mailer = {
    createMailerFromSession(getSession(config))
  }

  def createMailerFromSession(session: Session): Mailer = {
    Mailer(session)
  }
}
