package com.lucidchart.open.cashy.utils

import java.util.Date
import javax.inject.Inject

import scala.util.Random

import play.api.Configuration
import play.api.mvc.{
  Cookie,
  CookieBaker,
  Cookies,
  DiscardingCookie,
  UrlEncodedCookieDataCodec,
  RequestHeader,
  Session => PlaySession
}
import play.api.libs.crypto.CookieSigner

class Auth @Inject() (
    configuration: Configuration,
    sessionFactory: Auth.SessionFactory,
    sessionCookieBaker: Auth.SessionCookieBaker
) {

  import Auth._

  /**
    * The cookie used to discard auth headers
    */
  def discardingCookie =
    DiscardingCookie(
      sessionCookieBaker.COOKIE_NAME,
      sessionCookieBaker.path,
      sessionCookieBaker.domain,
      sessionCookieBaker.secure
    )

  /**
    * Generate the authentication cookie to send to the client.
    *
   * @param userId ID of the user that is logged in
    * @param rememberme True if the user does not want to login frequently
    * @param userAgent from the request
    * @return cookie
    */
  def generateAuthCookie(userId: Long, rememberme: Boolean, userAgent: String) = {
    val handler = if (rememberme) sessionCookieBaker.remembered else sessionCookieBaker
    val session = new Session(userId, new Date(), handler.maxTtlDate, rememberme, HashHelper.md5(userAgent))
    handler.encodeAsCookie(session.toSession)
  }

  /**
    * Parses the auth cookie given. Returns the session if the cookie
    * is valid.
    *
   * @param cookie
    * @param userAgent from the request
    * @return session info
    */
  def parseAuthCookie(cookie: Option[Cookie], userAgent: String) = {
    sessionFactory.fromCookie(cookie, HashHelper.md5(userAgent))
  }

  /**
    * Finds the auth cookie, parses it, and returns the session if the cookie
    * is valid.
    *
   * @param cookies
    * @param userAgent from the request
    * @return session info
    */
  def parseAuthCookie(cookies: Cookies, userAgent: String): Option[Session] = {
    parseAuthCookie(cookies.get(sessionCookieBaker.COOKIE_NAME), userAgent)
  }

  /**
    * Finds the auth cookie, parses it, and returns the session if the cookie
    * is valid.
    *
   * @param request
    * @return session info
    */
  def parseAuthCookie(request: RequestHeader): Option[Session] = {
    parseAuthCookie(
      request.cookies,
      request.headers.get("User-Agent").getOrElse("")
    )
  }

}

object Auth {

  /**
    * Copied & modified from play source.
    *
   * @see play.api.mvc.Session
    */
  class SessionCookieBaker @Inject() (configuration: Configuration, val cookieSigner: CookieSigner)
      extends CookieBaker[PlaySession]
      with UrlEncodedCookieDataCodec {
    val COOKIE_NAME = configuration.get[String]("auth.cookie.name")
    val emptyCookie = new PlaySession
    override val secure = configuration.get[Boolean]("auth.cookie.secure")
    override val isSigned = true
    override val httpOnly = true
    override val path = configuration.get[String]("auth.cookie.path")
    override val domain = configuration.getOptional[String]("auth.cookie.domain")
    override val maxAge: Option[Int] = None
    val ttl = configuration.get[Int]("auth.cookie.ttl") * 1000L

    def deserialize(data: Map[String, String]) = new PlaySession(data)
    def serialize(session: PlaySession) = session.data
    def maxTtlDate = new Date(System.currentTimeMillis + ttl)

    private[Auth] lazy val remembered: SessionCookieBaker =
      new SessionCookieBaker(configuration, cookieSigner) {
        override val maxAge = Some(configuration.get[Int]("auth.cookie.remembermeMaxAge"))
        override val ttl = maxAge.get * 1000L
      }
  }

  private val userIdKey = "u"
  private val createdKey = "c"
  private val expiresKey = "e"
  private val remembermeKey = "r"
  private val userAgentKey = "a"

  /**
    * Auth Session
    *
   * Contains user ID and created date for the session
    */
  case class Session(userId: Long, created: Date, expires: Date, rememberme: Boolean, userAgentHash: String) {

    /**
      * Check to see if the session has expired (regardless of whether
      * the client has enforced it or not).
      */
    def expired = expires.before(new Date())

    /**
      * Convert this AuthSession into a PlaySession
      */
    def toSession =
      PlaySession(
        Map(
          userIdKey -> userId.toString,
          createdKey -> (created.getTime / 1000).toString,
          expiresKey -> (expires.getTime / 1000).toString,
          remembermeKey -> (if (rememberme) "1" else "0"),
          userAgentKey -> userAgentHash
        )
      )
  }

  class SessionFactory @Inject() (sessionCookieBaker: Auth.SessionCookieBaker) {

    /**
      * Parse the details from a cookie, return the auth session information, if found.
      *
     * @param cookie
      * @return session
      */
    def fromCookie(cookie: Option[Cookie], userAgentHash: String): Option[Session] = {
      val playSession = sessionCookieBaker.decodeFromCookie(cookie)
      if (playSession.isEmpty) {
        None
      } else {
        try {
          val session = new Session(
            playSession(userIdKey).toLong,
            new Date(playSession(createdKey).toInt * 1000L),
            new Date(playSession(expiresKey).toInt * 1000L),
            playSession(remembermeKey) == "1",
            playSession(userAgentKey)
          )

          if (session.expired) {
            throw new Exception
          }

          if (session.userAgentHash != userAgentHash) {
            throw new Exception
          }

          Some(session)
        } catch {
          case e: Exception => None
        }
      }
    }
  }
}
