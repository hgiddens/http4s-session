name := "http4s-session-example"

resolvers += Resolver.bintrayRepo("hgiddens", "maven")
libraryDependencies ++= Seq(
  "com.github.hgiddens" %% "http4s-global-middleware" % "2.0.0",
  "io.circe" %% "circe-optics" % Versions.circe,
  "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
  "org.http4s" %% "http4s-dsl" % Versions.http4s
)
