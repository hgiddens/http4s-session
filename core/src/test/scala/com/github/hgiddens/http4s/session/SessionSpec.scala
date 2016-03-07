package com.github.hgiddens.http4s.session

import Syntax._
import argonaut.{Json, JsonObject}
import argonaut.Argonaut._
import monocle.Monocle
import monocle.Monocle._
import org.http4s.argonaut.{json, jsonEncoder}
import org.http4s.dsl._
import org.http4s.headers.{Cookie => CookieHeader, `Set-Cookie`}
import org.http4s.server.HttpService
import org.http4s.{Cookie, DateTime, Method, Request, Response, Status}
import org.specs2.ScalaCheck
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import scalaz.Scalaz._

object Generators {
  import org.scalacheck.{Arbitrary, Gen}
  import org.scalacheck.Arbitrary.arbitrary

  implicit def arbSession: Arbitrary[Session] =
    Arbitrary(for {
      string <- Gen.alphaStr
      number <- arbitrary[Int]
    } yield Json("string" := string, "number" := number))
}

object Matchers {
  import org.specs2.matcher.Matchers._

  /** Doesn't capture cookies that are being deleted. */
  def setCookies(response: Response): Vector[Cookie] =
    response.headers.collect {
      case `Set-Cookie`(setCookie) if setCookie.cookie.expires.forall(_ >= DateTime.now) =>
        setCookie.cookie
    }.toVector

  def expiredCookies(response: Response): Vector[Cookie] =
    response.headers.collect {
      case `Set-Cookie`(setCookie) if setCookie.cookie.expires.exists(_ < DateTime.now) =>
        setCookie.cookie
    }.toVector

  def beCookieWithName(name: String): Matcher[Cookie] =
    be_===(name) ^^ ((_: Cookie).name)

  def haveSetCookie(cookieName: String): Matcher[Response] =
    contain(beCookieWithName(cookieName)) ^^ setCookies _

  def haveClearedCookie(cookieName: String): Matcher[Response] =
    contain(beCookieWithName(cookieName)) ^^ expiredCookies _
}

object SessionSpec extends Specification with ScalaCheck {
  import Generators._
  import Matchers._

  val config = SessionConfig(
    cookieName = "session",
    mkCookie = Cookie(_, _)
  )

  val newSession = Json("created" := true)

  def sut: HttpService =
    Session.middleware(config)(HttpService {
      case GET -> Root / "id" =>
        Ok()

      case GET -> Root / "create" =>
        Ok().newSession(newSession)

      case GET -> Root / "clear" =>
        Ok().clearSession

      case req @ GET -> Root / "read" =>
        for {
          session <- req.session
          response <- session.cata(Ok(_), NotFound())
        } yield response

      case GET -> Root / "modify" =>
        val _number = jObjectPrism ^|-> at[JsonObject, String, Json]("number") ^<-? Monocle.some ^<-? jIntPrism
        Ok().modifySession(_number.modify(_ + 1))
    })

  "Doing nothing" should {
    "not clear or set a session cookie when there is no session" in {
      val request = Request(Method.GET, uri("/id"))
      val response = sut(request).run
      response must not(haveSetCookie(config.cookieName) or haveClearedCookie(config.cookieName))
    }

    "not clear a session cookie when one is set" in prop { session: Session =>
      val cookie = config.cookie(session.nospaces)
      val request = Request(Method.GET, uri("/id")).putHeaders(CookieHeader(cookie.wrapNel))
      val response = sut(request).run
      response must not(haveClearedCookie(config.cookieName))
    }
  }

  "Creating a session" should {
    "set a session cookie as per mkCookie" in {
      val request = Request(Method.GET, uri("/create"))
      val response = sut(request).run
      setCookies(response) must contain(config.cookie(newSession.nospaces))
    }
  }

  "Clearing a session" should {
    "clear session cookie when one is set" in prop { session: Session =>
      val cookie = config.cookie(session.nospaces)
      val request = Request(Method.GET, uri("/clear")).putHeaders(CookieHeader(cookie.wrapNel))
      val response = sut(request).run
      response must haveClearedCookie(config.cookieName)
    }
  }

  "Reading a session" should {
    "read None when there is no session" in {
      val request = Request(Method.GET, uri("/read"))
      val response = sut(request).run
      response.status ==== Status.NotFound
    }

    "read the session when it exists" in prop { session: Session =>
      val cookie = config.cookie(session.nospaces)
      val request = Request(Method.GET, uri("/read")).putHeaders(CookieHeader(cookie.wrapNel))
      val response = sut(request).as[Json].run
      response ==== session
    }
  }

  "Modifying a session" should {
    "update the session when set" in {
      val cookie = config.cookie(Json("number" := 0).nospaces)
      val request = Request(Method.GET, uri("/modify")).putHeaders(CookieHeader(cookie.wrapNel))
      (for {
        response <- sut(request)
        cookies = setCookies(response)
        secondRequest = Request(Method.GET, uri("/read")).putHeaders(CookieHeader(cookies.toList.toNel.get))
        secondResponse <- sut(secondRequest).as[Session]
      } yield secondResponse).run ==== Json("number" := 1)
    }

    "do nothing when not" in {
      val request = Request(Method.GET, uri("/modify"))
      val response = sut(request).run
      response must not(haveSetCookie(config.cookieName) or haveClearedCookie(config.cookieName))
    }
  }
}
