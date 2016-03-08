## Session middleware for http4s

[![Build Status](https://api.travis-ci.org/hgiddens/http4s-session.svg)](https://travis-ci.org/hgiddens/http4s-session)

A simple project defining [http4s](http://http4s.org) middleware for client-side session management. See the `example` project for a code example.

### `Session.sessionManagement`

Enables session management for the wrapped `Service`. Sessions are JSON values, and are stored on the client as HTTP cookies – having been signed and encrypted. The signing/encryption is by way of a server secret, which should be a long (e.g. 128 bytes) random string, and which much not be disclosed. Sessions can be invalidated by changing the secret.

### `Session.sessionRequired`

Serves a provided `Response` instead of using the wrapped `Service` if the request does not have a session.

### `Syntax`

The `Session.sessionManagement` middleware must be wrapping any `Service` or `Middleware` using these methods.

* `request.session` – gets the session from the request.
* `response.newSession` – sets the provided value as the session in the response.
* `response.clearSession` – clears the session in the response.
* `response.modifySession` – modifies the session that is sent back to the client in response.

### Credits

Heavily based on the awesome [akka-http-session](https://github.com/softwaremill/akka-http-session).
