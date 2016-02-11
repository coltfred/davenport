//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package db

import codec.{ ByteVectorDecoder, ByteVectorEncoder }
import scalaz.Order
import scalaz.std.string._

/** Just a string. This is used for type safety. */
final case class Key(value: String) extends AnyVal

object Key {
  implicit final val OrderInstance: Order[Key] = Order[String].contramap[Key](_.value)
  implicit final val OrderingInstance: scala.math.Ordering[Key] = OrderInstance.toScalaOrdering
}

/** Just a string. This is used for type safety. */
final case class RawJsonString(value: String) extends AnyVal
object RawJsonString {
  implicit val decoder: ByteVectorDecoder[RawJsonString] = ByteVectorDecoder.StringDecoder.map(RawJsonString(_))
  implicit val encoder: ByteVectorEncoder[RawJsonString] = ByteVectorEncoder.StringEncoder.contramap(_.value)
}

/**
 * A commit version of an existing value in the db.
 *
 *  Couchbase calls this a CAS (check and save) as it is passed back
 *  in with requests to update a value. If the value has been changed
 *  by another actor, then the update fails and the caller is left
 *  to handle the conflict.
 */
final case class CommitVersion(value: Long) extends AnyVal
