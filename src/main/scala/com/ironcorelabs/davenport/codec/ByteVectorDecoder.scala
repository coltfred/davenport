//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package codec

import scodec.bits.ByteVector
import scalaz._, Scalaz._
import argonaut._

/**
 * Decode a ByteVector into some type A
 */
trait ByteVectorDecoder[A] {
  def decode(bytes: ByteVector): DecodeError \/ A
  /**
   * Alias for [[ByteVectorDecoder.decode]].
   */
  def apply(b: ByteVector): DecodeError \/ A = decode(b)
  def map[B](f: A => B): ByteVectorDecoder[B] = ByteVectorDecoder.fromFunction { bytes => decode(bytes).map(f) }
}

final object ByteVectorDecoder {
  implicit final val FunctorInstance: Functor[ByteVectorDecoder] = new Functor[ByteVectorDecoder] {
    def map[A, B](decoder: ByteVectorDecoder[A])(f: A => B) = decoder.map(f)
  }
  implicit final val IdDecoder: ByteVectorDecoder[ByteVector] = ByteVectorDecoder.fromFunction { b => b.right }
  implicit final val StringDecoder: ByteVectorDecoder[String] = ByteVectorDecoder.fromFunction { b =>
    \/.fromEither(b.decodeUtf8).leftMap(ex => DecodeError("Couldn't decode the bytes into a utf8 string", Some(ex)))
  }

  implicit final val JsonDecoder: ByteVectorDecoder[Json] = ByteVectorDecoder.fromFunction { bytes =>
    for {
      string <- StringDecoder(bytes)
      result <- JsonParser.parse(string).leftMap(message => DecodeError(s"Json parse failed with '$message'"))
    } yield result
  }

  final def apply[A](implicit decoder: ByteVectorDecoder[A]): ByteVectorDecoder[A] = decoder

  final def fromFunction[A](f: ByteVector => DecodeError \/ A): ByteVectorDecoder[A] = new ByteVectorDecoder[A] {
    def decode(b: ByteVector): DecodeError \/ A = f(b)
  }

  final def fromDecodeJson[A](d: DecodeJson[A]): ByteVectorDecoder[A] = ByteVectorDecoder.fromFunction { bytes =>
    for {
      string <- StringDecoder(bytes)
      json <- JsonParser.parse(string).leftMap(message => DecodeError(s"Json parse failed with '$message'"))
      a <- d.decodeJson(json).toDisjunction.leftMap {
        case (message, history) =>
          new DecodeError(s"Failed to decode json giving excuse: '$message' at '$history'")
      }
    } yield a
  }
}

/**
 * Error indicating a failure to decode a value from a ByteVector.
 */
case class DecodeError(message: String, cause: Option[Exception] = None)
