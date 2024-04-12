package at.energydash
package actor

import admin.RegisterPontonServiceHandler
import admin.mail.SendMailServiceHandler
import config.Config
import domain.email.ConfiguredMailer
import services.{AdminServiceImpl, SendMailServiceImpl}

import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.pki.pem.{DERPrivateKeyLoader, PEMDecoder}

import java.security.cert.{Certificate, CertificateFactory}
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.{Failure, Success}

object AdminServer {

  private def adminMailConfig = Config.adminSmtpConfig
  def mailSession = ConfiguredMailer.getAdminSession(adminMailConfig)
  def apply(mailService: ActorRef[EmailCommand], system: ActorSystem[_]) = new AdminServer(mailService, system).run
}

class AdminServer(tenantProvider: ActorRef[EmailCommand], system: ActorSystem[_]) {
  def run(): Future[Http.ServerBinding] = {
    implicit val sys = system
    implicit val ec: ExecutionContext = system.executionContext
    implicit val sch: Scheduler = system.scheduler

//    val mailerService: PartialFunction[HttpRequest, Future[HttpResponse]] =
//      ExcelAdminServiceHandler.partial(new ExcelServiceImpl(AdminServer.mailSession, system))

    val mailerService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      SendMailServiceHandler.partial(new SendMailServiceImpl(AdminServer.mailSession))

    val adminService: PartialFunction[HttpRequest, Future[HttpResponse]] = {
      RegisterPontonServiceHandler.partial(new AdminServiceImpl(tenantProvider))
    }

    val services: HttpRequest => Future[HttpResponse] = ServiceHandler.concatOrNotFound(mailerService, adminService)

    val bound: Future[Http.ServerBinding] = Http(system)
      .newServerAt(interface = "0.0.0.0", port = 9093)
//      .enableHttps(serverHttpContext)
      .bind(services)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 20.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        println("gRPC server bound to {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        println("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }

    bound
  }

  private def serverHttpContext: HttpsConnectionContext = {
    val privateKey =
      DERPrivateKeyLoader.load(PEMDecoder.decode(readPrivateKeyPem()))
    val fact = CertificateFactory.getInstance("X.509")
    val cer = fact.generateCertificate(
      classOf[AdminServer].getResourceAsStream("/certs/server1.pem")
    )
    val ks = KeyStore.getInstance("PKCS12")
    ks.load(null)
    ks.setKeyEntry(
      "private",
      privateKey,
      new Array[Char](0),
      Array[Certificate](cer)
    )
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, null)
    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, null, new SecureRandom)
    ConnectionContext.https(context)
  }

  private def readPrivateKeyPem(): String =
    Source.fromResource("certs/server1.key").mkString
}
