package com.lucidchart.open.cashy.utils

import javax.inject.Inject
import org.apache.commons.mail.HtmlEmail
import play.api.{Configuration, Logger}

case class MailerSMTPConfiguration(
    host: String,
    port: Int,
    user: String,
    pass: String
)

case class MailerAddress(
    email: String,
    name: String = ""
)

case class MailerMessage(
    from: MailerAddress,
    reply: Option[MailerAddress] = None,
    to: Iterable[MailerAddress],
    cc: Iterable[MailerAddress] = Nil,
    bcc: Iterable[MailerAddress] = Nil,
    subject: String,
    text: String
)

class Mailer @Inject() (configuration: Configuration) {
  private[this] val logger = Logger(this.getClass)

  private val enabled = configuration.get[Boolean]("mailer.enabled")
  private val smtpConfig = MailerSMTPConfiguration(
    configuration.get[String]("mailer.smtp.host"),
    configuration.get[Int]("mailer.smtp.port"),
    configuration.get[String]("mailer.smtp.user"),
    configuration.get[String]("mailer.smtp.pass")
  )

  /**
    * Send an email message
    *
   * Throws any and all exceptions
    *
   * @param message Details about the message to send
    */
  def send(message: MailerMessage): Unit = {
    if (!enabled) {
      logger.info(
        "Not sending email to " + message.to + " with subject '" + message.subject + "' because the mailer is disabled."
      )
    } else {
      val email = new HtmlEmail()

      email.setSmtpPort(smtpConfig.port)
      email.setHostName(smtpConfig.host)
      email.setAuthentication(smtpConfig.user, smtpConfig.pass)

      email.setTextMsg(message.text)
      email.setSubject(message.subject)
      email.setFrom(message.from.email, message.from.name)

      message.reply.map { reply =>
        email.addReplyTo(reply.email, reply.name)
      }

      message.to.foreach { to =>
        email.addTo(to.email, to.name)
      }

      message.cc.foreach { cc =>
        email.addCc(cc.email, cc.name)
      }

      message.bcc.foreach { bcc =>
        email.addBcc(bcc.email, bcc.name)
      }

      email.send()
    }
  }
}
