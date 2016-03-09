package com.github.hgiddens.http4s.session
package example

import Syntax._
import argonaut.{JsonObject, Json}
import argonaut.Argonaut._
import com.github.hgiddens.http4s.middleware.global.Syntax._
import monocle.Monocle._
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, EntityEncoder, Cookie}
import org.http4s.dsl._
import org.http4s.server.{ServerBuilder, HttpService}
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.duration._
import scala.xml.Elem
import scalaz.OptionT
import scalaz.concurrent.{Task, TaskApp}

object Example extends TaskApp {
  implicit def xmlAsHtmlEncoder: EntityEncoder[Elem] =
    EntityEncoder.stringEncoder(org.http4s.DefaultCharset)
      .contramap[Elem](xml => xml.buildString(false))
      .withContentType(`Content-Type`(MediaType.`text/html`))

  def loginService: HttpService =
    HttpService {
      case GET -> Root =>
        Ok {
          <html>
            <head><title>Log in</title></head>
            <body><form method="post"><button>Log in</button></form></body>
          </html>
        }

      case POST -> Root =>
        SeeOther(uri("/")).newSession(Json("name" := "J. Doe"))
    }

  def logoutService: HttpService =
    HttpService {
      case POST -> Root =>
        SeeOther(uri("/")).clearSession
    }

  def protectedService: HttpService =
    HttpService {
      case req @ GET -> Root =>
        OptionT(req.session).flatMapF { session =>
          val name = jObjectPrism ^|-> at[JsonObject, String, Json]("name") ^<-? some ^<-? jStringPrism
          Ok {
            <html>
              <head><title>Protected</title></head>
              <body>
                <form method="post" action="/logout"><button>Log out</button></form>
                <p>Hello, { name.getOption(session).getOrElse("Someone") }</p>
              </body>
            </html>
          }
        }.getOrElseF(Forbidden())
    }

  def serverBuilder: ServerBuilder = {
    val config = SessionConfig(
      cookieName = "session",
      mkCookie = Cookie(_, _),
      secret = "This is a secret",
      maxAge = 5.minutes
    )
    BlazeBuilder.
      bindHttp(port = 8080).
      globalMiddleware(Session.sessionManagement(config)).
      mountService(loginService, "/login").
      mountService(logoutService, "/logout").
      mountService(Session.sessionRequired(SeeOther(uri("/login")))(protectedService), "/")
  }

  override def runc =
    for {
      server <- serverBuilder.start
      _ <- Task.delay {
        println("Server started up on port 8080")
        server.awaitShutdown()
      }
    } yield ()
}
