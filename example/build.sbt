name := "http4s-session-example"

resolvers += Resolver.bintrayRepo("hgiddens", "maven")
libraryDependencies ++= Seq(
  "com.github.hgiddens" %% "http4s-global-middleware" % "1.0.0",
  "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
  "org.http4s" %% "http4s-dsl" % Versions.http4s
)
