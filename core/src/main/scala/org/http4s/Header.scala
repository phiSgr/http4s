/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpHeader.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s

import cats.{Order, Show}
import cats.data.NonEmptyList
import cats.syntax.all._
import org.http4s.util._
import org.typelevel.ci.CIString
import scala.util.hashing.MurmurHash3

/** Abstract representation o the HTTP header
  * @see org.http4s.HeaderKey
  */
@deprecated("Condemned", "LSUG")
sealed trait Header extends Renderable with Product {
  import Header.Raw

  def name: CIString

  def parsed: Header

  def renderValue(writer: Writer): writer.type

  def value: String = {
    val w = new StringWriter
    renderValue(w).result
  }

  def is(key: HeaderKey): Boolean = key.matchHeader(this).isDefined

  def isNot(key: HeaderKey): Boolean = !is(key)

  override def toString: String = name.toString + ": " + value

  def toRaw: Raw = Raw(name, value)

  final def render(writer: Writer): writer.type = {
    writer << name << ':' << ' '
    renderValue(writer)
  }

  final override def hashCode(): Int =
    MurmurHash3.mixLast(name.hashCode, MurmurHash3.productHash(parsed))

  final override def equals(that: Any): Boolean =
    that match {
      case h: AnyRef if this eq h => true
      case h: Header =>
        (name == h.name) &&
          (parsed.productArity == h.parsed.productArity) &&
          (parsed.productIterator.sameElements(h.parsed.productIterator))
      case _ => false
    }

  /** Length of the rendered header, including name and final '\r\n' */
  def renderedLength: Long =
    render(new HeaderLengthCountingWriter).length + 2
}

@deprecated("Condemned", "LSUG")
object Header {
  def unapply(header: Header): Option[(CIString, String)] =
    Some((header.name, header.value))

  def apply(name: String, value: String): Raw = Raw(CIString(name), value)

  /** Raw representation of the Header
    *
    * This can be considered the simplest representation where the header is specified as the product of
    * a key and a value
    * @param name case-insensitive string used to identify the header
    * @param value String representation of the header value
    */
  @deprecated("Condemned", "LSUG")
  final case class Raw(name: CIString, override val value: String) extends Header {
    final override def parsed: Header = null // I told you it was condemned
    override def renderValue(writer: Writer): writer.type = writer.append(value)
  }

  /** A Header that is already parsed from its String representation. */
  @deprecated("Condemned", "LSUG")
  trait Parsed extends Header {
    def key: HeaderKey
    def name: CIString = key.name
    def parsed: this.type = this
  }

  /** A recurring header that satisfies this clause of the Spec:
    *
    * Multiple message-header fields with the same field-name MAY be present in a message if and only if the entire
    * field-value for that header field is defined as a comma-separated list [i.e., #(values)]. It MUST be possible
    * to combine the multiple header fields into one "field-name: field-value" pair, without changing the semantics
    * of the message, by appending each subsequent field-value to the first, each separated by a comma.
    */
  @deprecated("Condemned", "LSUG")
  trait Recurring extends Parsed {
    type Value
    def values: NonEmptyList[Value]
  }

  /** Simple helper trait that provides a default way of rendering the value */
  @deprecated("Condemned", "LSUG")
  trait RecurringRenderable extends Recurring {
    type Value <: Renderable
    override def renderValue(writer: Writer): writer.type = {
      values.head.render(writer)
      values.tail.foreach(writer << ", " << _)
      writer
    }
  }

  /** Helper trait that provides a default way of rendering the value provided a Renderer */
  @deprecated("Condemned", "LSUG")
  trait RecurringRenderer extends Recurring {
    type Value
    implicit def renderer: Renderer[Value]
    override def renderValue(writer: Writer): writer.type = {
      renderer.render(writer, values.head)
      values.tail.foreach(writer << ", " << Renderer.renderString(_))
      writer
    }
  }

  implicit val HeaderShow: Show[Header] = Show.show[Header] {
    _.toString
  }

  implicit lazy val HeaderOrder: Order[Header] =
    Order.from { case (a, b) =>
      val nameComparison: Int = a.name.compare(b.name)
      if (nameComparison === 0) {
        a.value.compare(b.value)
      } else {
        nameComparison
      }
    }
}
