package com.github.hgiddens.http4s.session

import io.circe.jawn
import java.util.Date
import javax.crypto.{Cipher, Mac}
import javax.crypto.spec.SecretKeySpec
import javax.xml.bind.DatatypeConverter
import org.http4s.{AttributeKey, Cookie, Request, Response}
import org.http4s.Http4s._
import org.http4s.headers.{Cookie => CookieHeader}
import org.http4s.server.{Middleware, HttpMiddleware}
import scala.concurrent.duration.Duration
import scala.util.Try
import scalaz.OptionT
import scalaz.Scalaz._
import scalaz.concurrent.Task

object Syntax {
  implicit final class RequestOps(val v: Request) extends AnyVal {
    def session: Task[Option[Session]] =
      Task.now(v.attributes.get(Session.requestAttr))
  }

  implicit final class TaskResponseOps(val v: Task[Response]) extends AnyVal {
    def clearSession: Task[Response] =
      v.withAttribute(Session.responseAttr(_ => None))

    def modifySession(f: Session => Session): Task[Response] = {
      val lf: Option[Session] => Option[Session] = _.cata(f.andThen(_.some), None)
      v.map { response =>
        response.withAttribute(Session.responseAttr(response.attributes.get(Session.responseAttr).cata(_.andThen(lf), lf)))
      }
    }

    def newSession(session: Session): Task[Response] =
      v.withAttribute(Session.responseAttr(_ => Some(session)))
  }
}

final case class SessionConfig(
    cookieName: String,
    mkCookie: (String, String) => Cookie,
    secret: String,
    maxAge: Duration
) {
  // TODO: Type for this
  require(secret.length >= 16)

  private[session] def constantTimeEquals(a: String, b: String): Boolean =
    if (a.length != b.length) {
      false
    } else {
      var equal = 0
      for (i <- Array.range(0, a.length)) {
        equal |= a(i) ^ b(i)
      }
      equal == 0
    }

  private[this] def keySpec: SecretKeySpec =
    new SecretKeySpec(secret.substring(0, 16).getBytes("UTF-8"), "AES")

  private[this] def encrypt(content: String): String = {
    // akka-http-session pads content to guarantee it's non-empty
    // we require maxAge so it can never be empty.
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    DatatypeConverter.printHexBinary(cipher.doFinal(content.getBytes("UTF-8")))
  }

  private[this] def decrypt(content: String): Option[String] = {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, keySpec)
    Try(new String(cipher.doFinal(DatatypeConverter.parseHexBinary(content)), "UTF-8")).toOption
  }

  private[this] def sign(content: String): String = {
    val signKey = secret.getBytes("UTF-8")
    val signMac = Mac.getInstance("HmacSHA1")
    signMac.init(new SecretKeySpec(signKey, "HmacSHA256"))
    DatatypeConverter.printBase64Binary(signMac.doFinal(content.getBytes("UTF-8")))
  }

  private[session] def cookie(content: String): Task[Cookie] =
    Task.delay {
      val now = new Date().getTime / 1000
      val expires = now + maxAge.toSeconds
      val serialized = s"$expires-$content"
      val signed = sign(serialized)
      val encrypted = encrypt(serialized)
      mkCookie(cookieName, s"$signed-$encrypted")
    }

  private[session] def check(cookie: Cookie): Task[Option[String]] =
    Task.delay {
      val now = new Date().getTime / 1000
      cookie.content.split('-') match {
        case Array(signature, value) =>
          for {
            decrypted <- decrypt(value)
            if constantTimeEquals(signature, sign(decrypted))
            Array(expires, content) = decrypted.split("-", 2)
            expiresSeconds <- Try(expires.toLong).toOption
            if expiresSeconds > now
          } yield content
        case _ =>
          None
      }
    }
}

object Session {
  val requestAttr = AttributeKey[Session]("com.github.hgiddens.http4s.session.Session")
  val responseAttr = AttributeKey[Option[Session] => Option[Session]]("com.github.hgiddens.http4s.session.Session")

  private[this] def sessionAsCookie(config: SessionConfig, session: Session): Task[Cookie] =
    config.cookie(session.noSpaces)

  private[this] def checkSignature(config: SessionConfig, cookie: Cookie): Task[Option[Session]] =
    config.check(cookie).map(_.flatMap(jawn.parse(_).toOption))

  private[this] def sessionFromRequest(config: SessionConfig, request: Request): Task[Option[Session]] =
    (for {
      allCookies <- OptionT(Task.now(CookieHeader.from(request.headers)))
      sessionCookie <- OptionT(Task.now(allCookies.values.list.find(_.name === config.cookieName)))
      session <- OptionT(checkSignature(config, sessionCookie))
    } yield session).run

  def sessionManagement(config: SessionConfig): HttpMiddleware =
    Middleware { (request, service) =>
      for {
        requestSession <- sessionFromRequest(config, request)
        requestWithSession = requestSession.cata(
          session => request.withAttribute(requestAttr, session),
          request
        )
        response <- service(requestWithSession)
        updateSession = response.attributes.get(responseAttr) | identity
        responseWithSession <- updateSession(requestSession).cata(
          session => sessionAsCookie(config, session).map(response.addCookie),
          Task.now(if (requestSession.isDefined) response.removeCookie(config.cookieName) else response)
        )
      } yield responseWithSession
    }

  def sessionRequired(fallback: Task[Response]): HttpMiddleware =
    Middleware { (request, service) =>
      import Syntax._
      OptionT(request.session).flatMapF(_ => service(request)).getOrElseF(fallback)
    }
}
