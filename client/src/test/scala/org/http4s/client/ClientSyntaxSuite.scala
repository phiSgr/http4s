/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package client

import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.all._
import fs2._
import org.http4s.Method._
import org.http4s.Status.{BadRequest, Created, InternalServerError, Ok}
import org.http4s.syntax.all._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Accept

class ClientSyntaxSuite extends Http4sSuite with Http4sClientDsl[IO] {
  val app = HttpRoutes
    .of[IO] {
      case r if r.method == GET && r.pathInfo == "/" =>
        Response[IO](Ok).withEntity("hello").pure[IO]
      case r if r.method == PUT && r.pathInfo == "/put" =>
        Response[IO](Created).withEntity(r.body).pure[IO]
      case r if r.method == GET && r.pathInfo == "/echoheaders" =>
        r.headers.get(Accept).fold(IO.pure(Response[IO](BadRequest))) { m =>
          Response[IO](Ok).withEntity(m.toString).pure[IO]
        }
      case r if r.pathInfo == "/status/500" =>
        Response[IO](InternalServerError).withEntity("Oops").pure[IO]
    }
    .orNotFound

  val client: Client[IO] = Client.fromHttpApp(app)

  val req: Request[IO] = Request(GET, uri"http://www.foo.bar/")

  object SadTrombone extends Exception("sad trombone")

  def assertDisposes(f: Client[IO] => IO[Unit]): IO[Unit] = {
    var disposed = false
    val dispose = IO {
      disposed = true
      ()
    }
    val disposingClient = Client { (req: Request[IO]) =>
      Resource.make(app(req))(_ => dispose)
    }
    f(disposingClient).attempt.map(_ => disposed).assert
  }

  test("Client should ggmatch responses to Uris with get") {
    client
      .get(req.uri) {
        case Ok(_) => IO.pure("Ok")
        case _ => IO.pure("fail")
      }
      .assertEquals("Ok")
  }

  test("Client should ggmatch responses to requests with run") {
    client
      .run(req)
      .use {
        case Ok(_) => IO.pure("Ok")
        case _ => IO.pure("fail")
      }
      .assertEquals("Ok")
  }

  test("Client should ggget disposes of the response on success") {
    assertDisposes(_.get(req.uri) { _ =>
      IO.unit
    })
  }

  test("Client should ggget disposes of the response on failure") {
    assertDisposes(_.get(req.uri) { _ =>
      IO.raiseError(SadTrombone)
    })
  }

  test("Client should ggget disposes of the response on uncaught exception") {
    assertDisposes(_.get(req.uri) { _ =>
      sys.error("Don't do this at home, kids")
    })
  }

  test("Client should ggrun disposes of the response on success") {
    assertDisposes(_.run(req).use { _ =>
      IO.unit
    })
  }

  test("Client should ggrun disposes of the response on failure") {
    assertDisposes(_.run(req).use { _ =>
      IO.raiseError(SadTrombone)
    })
  }

  test("Client should ggrun disposes of the response on uncaught exception") {
    assertDisposes(_.run(req).use { _ =>
      sys.error("Don't do this at home, kids")
    })
  }

  test("Client should ggrun that does not match results in failed task") {
    client
      .run(req)
      .use(PartialFunction.empty)
      .attempt
      .map {
        case Left(_: MatchError) => true
        case _ => false
      }
      .assert
  }

  test("Client should ggfetch Uris with expect") {
    client.expect[String](req.uri).assertEquals("hello")
  }

  test("Client should ggfetch Uris with expectOr") {
    client
      .expectOr[String](req.uri) { _ =>
        IO.pure(SadTrombone)
      }
      .assertEquals("hello")
  }

  test("Client should ggfetch requests with expect") {
    client.expect[String](req).assertEquals("hello")
  }

  test("Client should ggfetch requests with expectOr") {
    client
      .expectOr[String](req) { _ =>
        IO.pure(SadTrombone)
      }
      .assertEquals("hello")
  }

  test("Client should ggfetch request tasks with expect") {
    client.expect[String](IO.pure(req)).assertEquals("hello")
  }

  test("Client should ggfetch request tasks with expectOr") {
    client
      .expectOr[String](IO.pure(req)) { _ =>
        IO.pure(SadTrombone)
      }
      .assertEquals("hello")
  }

  test("Client should ggstatus returns the status for a request") {
    client.status(req).assertEquals(Status.Ok)
  }

  test("Client should ggstatus returns the status for a request task") {
    client.status(IO.pure(req)).assertEquals(Status.Ok)
  }

  test("Client should ggsuccessful returns the success of the status for a request") {
    client.successful(req).assert
  }

  test("Client should ggsuccessful returns the success of the status for a request task") {
    client.successful(IO.pure(req)).assert
  }

  test("Client should ggstatus returns the status for a request") {
    client.status(req).assertEquals(Status.Ok)
  }

  test("Client should ggstatus returns the status for a request task") {
    client.status(IO.pure(req)).assertEquals(Status.Ok)
  }

  test("Client should ggsuccessful returns the success of the status for a request") {
    client.successful(req).assert
  }

  test("Client should ggsuccessful returns the success of the status for a request task") {
    client.successful(IO.pure(req)).assert
  }

  test(
    "Client should ggreturn an unexpected status when expecting a URI returns unsuccessful status") {
    client
      .expect[String](uri"http://www.foo.com/status/500")
      .attempt
      .assertEquals(Left(UnexpectedStatus(Status.InternalServerError)))
  }

  test("Client should gghandle an unexpected status when calling a URI with expectOr") {
    case class Boom(status: Status, body: String) extends Exception
    client
      .expectOr[String](uri"http://www.foo.com/status/500") { resp =>
        resp.as[String].map(Boom(resp.status, _))
      }
      .attempt
      .assertEquals(Left(Boom(InternalServerError, "Oops")))
  }

  test("Client should ggadd Accept header on expect") {
    client.expect[String](uri"http://www.foo.com/echoheaders").assertEquals("Accept: text/*")
  }

  test("Client should ggadd Accept header on expect for requests") {
    client
      .expect[String](Request[IO](GET, uri"http://www.foo.com/echoheaders"))
      .assertEquals("Accept: text/*")
  }

  test("Client should ggadd Accept header on expect for requests") {
    client
      .expect[String](Request[IO](GET, uri"http://www.foo.com/echoheaders"))
      .assertEquals("Accept: text/*")
  }

  test("Client should ggcombine entity decoder media types correctly") {
    // This is more of an EntityDecoder spec
    val edec =
      EntityDecoder.decodeBy[IO, String](MediaType.image.jpeg)(_ => DecodeResult.success("foo!"))
    client
      .expect(Request[IO](GET, uri"http://www.foo.com/echoheaders"))(
        EntityDecoder.text[IO].orElse(edec))
      .assertEquals("Accept: text/*, image/jpeg")
  }

  test("Client should ggreturn empty with expectOption and not found") {
    client
      .expectOption[String](Request[IO](GET, uri"http://www.foo.com/random-not-found"))
      .assertEquals(Option.empty[String])
  }
  test("Client should ggreturn expected value with expectOption and a response") {
    client
      .expectOption[String](Request[IO](GET, uri"http://www.foo.com/echoheaders"))
      .assertEquals(
        "Accept: text/*".some
      )
  }

  test("Client should ggstream returns a stream") {
    client
      .stream(req)
      .flatMap(_.body.through(fs2.text.utf8Decode))
      .compile
      .toVector
      .assertEquals(Vector("hello"))
  }

  test("Client should ggstreaming disposes of the response on success") {
    assertDisposes(_.stream(req).compile.drain)
  }

  test("Client should ggstreaming disposes of the response on failure") {
    assertDisposes(_.stream(req).flatMap(_ => Stream.raiseError[IO](SadTrombone)).compile.drain)
  }

  test("Client should ggtoService disposes of the response on success") {
    assertDisposes(_.toKleisli(_ => IO.unit).run(req))
  }

  test("Client should ggtoService disposes of the response on failure") {
    assertDisposes(_.toKleisli(_ => IO.raiseError(SadTrombone)).run(req))
  }

  test("Client should ggtoHttpApp disposes the response if the body is run") {
    assertDisposes(_.toHttpApp.flatMapF(_.body.compile.drain).run(req))
  }

  test("Client should ggtoHttpApp disposes of the response if the body is run, even if it fails") {
    assertDisposes(
      _.toHttpApp
        .flatMapF(_.body.flatMap(_ => Stream.raiseError[IO](SadTrombone)).compile.drain)
        .run(req))
  }

  test("Client should ggtoHttpApp allows the response to be read") {
    client.toHttpApp(req).flatMap(_.as[String]).assertEquals("hello")
  }

  test("Client should ggtoHttpApp disposes of resources in reverse order of acquisition") {
    Ref[IO]
      .of(Vector.empty[Int])
      .flatMap { released =>
        Client[IO] { _ =>
          for {
            _ <- List(1, 2, 3).traverse { i =>
              Resource(IO.pure(() -> released.update(_ :+ i)))
            }
          } yield Response()
        }.toHttpApp(req).flatMap(_.as[Unit]) >> released.get
      }
      .assertEquals(Vector(3, 2, 1))
  }

  test("Client should ggtoHttpApp releases acquired resources on failure") {
    Ref[IO]
      .of(Vector.empty[Int])
      .flatMap { released =>
        Client[IO] { _ =>
          for {
            _ <- List(1, 2, 3).traverse { i =>
              Resource(IO.pure(() -> released.update(_ :+ i)))
            }
            _ <- Resource.liftF[IO, Unit](IO.raiseError(SadTrombone))
          } yield Response()
        }.toHttpApp(req).flatMap(_.as[Unit]).attempt >> released.get
      }
      .assertEquals(Vector(3, 2, 1))
  }

  test("RequestResponseGenerator should Generate requests based on Method") {
    // The PUT: /put path just echoes the body
    client.expect[String](GET(uri"http://www.foo.com/")).assertEquals("hello") *>
      client.expect[String](PUT("hello?", uri"http://www.foo.com/put")).assertEquals("hello?")
  }
}
