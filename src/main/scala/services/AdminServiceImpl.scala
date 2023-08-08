package at.energydash
package services

import admin.{RegisterPontonRequest, RegisterPontonService, RegisteredPontonReply}

import akka.actor.typed.{ActorRef, Scheduler}

import scala.concurrent.{ExecutionContext, Future}
import actor.commands.EmailCommand

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}
import model.dao.TenantConfig

class AdminServiceImpl(actorRef: ActorRef[EmailCommand])(implicit val sch: Scheduler, ec: ExecutionContext) extends RegisterPontonService {

  implicit val timeout = Timeout(10.seconds)
  /**
   * Sends a greeting
   */
  override def register(in: RegisterPontonRequest): Future[RegisteredPontonReply] = {
    import actor.TenantProvider._

    val tenantConfig = TenantConfig(tenant = in.tenant,
      domain = in.domain, host = s"mail.${in.domain}", imapPort = 143,
      smtpHost = s"mail.${in.domain}", smtpPort = 25,
      user = in.tenant.toLowerCase, passwd = in.password, imapSecurity = "STARTTLS", smtpSecurity ="STARTTLS", active = true)

    actorRef.ask(ref => AddTenant(tenantConfig, ref)).transform {
      case Success(res) => res match {
        case ResponseOk => Try(RegisteredPontonReply(200, "OK"))
        case ResponseError(msg) => Try(RegisteredPontonReply(500, msg))
      }
      case Failure(e) =>
        Try(RegisteredPontonReply(500, e.getMessage))
    }
  }
}
