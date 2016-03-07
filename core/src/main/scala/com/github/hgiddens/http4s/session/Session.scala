package com.github.hgiddens.http4s.session

import argonaut.Argonaut._
import org.http4s.{AttributeKey, Cookie, Request, Response}
import org.http4s.Http4s._
import org.http4s.headers.{Cookie => CookieHeader}
import org.http4s.server.{Middleware, HttpMiddleware}
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
    mkCookie: (String, String) => Cookie
) {
  def cookie: String => Cookie =
    mkCookie.curried(cookieName)
}

object Session {
  private[session] val requestAttr = AttributeKey[Session]("com.github.hgiddens.http4s.session.Session")
  private[session] val responseAttr = AttributeKey[Option[Session] => Option[Session]]("com.github.hgiddens.http4s.session.Session")

  private[this] def sessionAsCookie(config: SessionConfig, session: Session): Cookie =
    config.cookie(session.nospaces)

  private[this] def sessionFromRequest(config: SessionConfig, request: Request): Option[Session] =
    for {
      allCookies <- CookieHeader.from(request.headers)
      sessionCookie <- allCookies.values.list.find(_.name === config.cookieName)
      session <- sessionCookie.content.parseOption
    } yield session

  def middleware(config: SessionConfig): HttpMiddleware =
    Middleware { (request, service) =>
      val requestSession = sessionFromRequest(config, request)
      val requestWithSession = requestSession.cata(
        session => request.withAttribute(requestAttr, session),
        request
      )
      service(requestWithSession).map { response =>
        val updateSession = response.attributes.get(responseAttr) | identity
        updateSession(requestSession).cata(
          session => response.addCookie(sessionAsCookie(config, session)),
          if (requestSession.isDefined) response.removeCookie(config.cookieName) else response
        )
      }
    }
}
