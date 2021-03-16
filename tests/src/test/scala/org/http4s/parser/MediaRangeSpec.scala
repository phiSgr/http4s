/*
 * Copyright 2013 http4s.org
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
package parser

import org.http4s.MediaRange._
import org.http4s.MediaType

class MediaRangeSpec extends Http4sSuite {
  val `text/asp`: MediaType =
    new MediaType("text", "asp", MediaType.Compressible, MediaType.NotBinary, List("asp"))
  val `audio/aiff`: MediaType =
    new MediaType(
      "audio",
      "aiff",
      MediaType.Compressible,
      MediaType.Binary,
      List("aif", "aiff", "aifc"))

  def ext = Map("foo" -> "bar")

  test("MediaRanges should Perform equality correctly") {
    assertEquals(AllText, AllText)

    assertEquals(AllText.withExtensions(ext), AllText.withExtensions(ext))
    assertNotEquals(AllText.withExtensions(ext), AllText)

    assertNotEquals(AllText, AllAudio)
  }

  test("MediaRanges should Be satisfiedBy MediaRanges correctly") {
    assertEquals(AllText.satisfiedBy(AllText), true)

    assertEquals(AllText.satisfiedBy(AllImages), false)
  }

  test("MediaRanges should Be satisfiedBy MediaTypes correctly") {
    assertEquals(AllText.satisfiedBy(MediaType.text.css), true)
    assertEquals(AllText.satisfiedBy(MediaType.text.css), true)
    assertEquals(AllText.satisfiedBy(`audio/aiff`), false)
  }

  test("MediaRanges should be satisfied regardless of extensions") {
    assertEquals(AllText.withExtensions(ext).satisfies(AllText), true)
    assertEquals(AllText.withExtensions(ext).satisfies(AllText), true)
  }

  test("MediaTypes should Perform equality correctly") {
    assertEquals(MediaType.text.html, MediaType.text.html)

    assertNotEquals(MediaType.text.html.withExtensions(ext), MediaType.text.html)

    assertNotEquals(MediaType.text.html, MediaType.text.css)
  }

  test("MediaTypes should Be satisfiedBy MediaTypes correctly") {
    assertEquals(MediaType.text.html.satisfiedBy(MediaType.text.css), false)
    assertEquals(MediaType.text.html.satisfiedBy(MediaType.text.html), true)

    assertEquals(MediaType.text.html.satisfies(MediaType.text.css), false)
  }

  test("MediaTypes should Not be satisfied by MediaRanges") {
    assertEquals(MediaType.text.html.satisfiedBy(AllText), false)
  }

  test("MediaTypes should Satisfy MediaRanges") {
    assertEquals(MediaType.text.html.satisfies(AllText), true)
    assertEquals(AllText.satisfies(MediaType.text.html), false)
  }

  test("MediaTypes should be satisfied regardless of extensions") {
    assertEquals(MediaType.text.html.withExtensions(ext).satisfies(AllText), true)
    assertEquals(AllText.satisfies(MediaType.text.html.withExtensions(ext)), false)

    assertEquals(MediaType.text.html.satisfies(AllText.withExtensions(ext)), true)
    assertEquals(AllText.withExtensions(ext).satisfies(MediaType.text.html), false)
  }

  test("MediaRanges and MediaTypes should Do inequality amongst each other properly") {
    val r = AllText
    val t = `text/asp`

    assert(r != t)
    assert(t != r)

    assert(r.withExtensions(ext) != t.withExtensions(ext))
    assert(t.withExtensions(ext) != r.withExtensions(ext))
  }
}
