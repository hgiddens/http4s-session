name := "http4s-session-core"

libraryDependencies ++= Seq(
  "io.argonaut" %% "argonaut" % "6.1",
  "org.http4s" %% "http4s-argonaut" % Versions.http4s % Test,
  "org.http4s" %% "http4s-dsl" % Versions.http4s % Test,
  "org.http4s" %% "http4s-server" % Versions.http4s,
  "org.specs2" %% "specs2-scalacheck" % Versions.specs2 % Test
)
