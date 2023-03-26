package at.energydash
package domain.email

import at.energydash.domain.dao.model.TenantConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ConfiguredMailerSpec extends AnyWordSpecLike with Matchers {

  "ConfiguredMailer" should {
    "Merge DB and Config" in {
      val tenantConfig = TenantConfig("myeeg", "email.com", "email.com", 0, 0, "sepp", "password", "", "", true)
      val session = ConfiguredMailer.getSession(tenantConfig)

      session.getProperties.get("mail.ssl.enable") shouldBe false
      session.getProperties.get("mail.imap.class") shouldBe "org.jvnet.mock_javamail.MockStore"
      session.getProperties.get("mail.mocked.class") shouldBe "org.jvnet.mock_javamail.MockTransport"
      println(session.getProperties)
      println(session.getProperty("mail.smtp.host"))
    }
  }

}
