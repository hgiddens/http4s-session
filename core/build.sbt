name := "http4s-session-core"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % Versions.circe,
  "io.circe" %% "circe-jawn" % Versions.circe,
  "io.circe" %% "circe-optics" % Versions.circe % Test,
  "org.http4s" %% "http4s-dsl" % Versions.http4s % Test,
  "org.http4s" %% "http4s-jawn" % Versions.http4s % Test,
  "org.http4s" %% "http4s-server" % Versions.http4s,
  "org.specs2" %% "specs2-matcher-extra" % Versions.specs2 % Test,
  "org.specs2" %% "specs2-scalacheck" % Versions.specs2 % Test
)
