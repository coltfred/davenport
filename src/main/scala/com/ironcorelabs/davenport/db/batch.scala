//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport.db
import fs2.Stream
import cats.Foldable
import cats.syntax.foldable._

/**
 * Object that contains batch operations for DB. They have been separated out because they cannot be mixed with
 * `DBProg` operations without first lifting them into a process via `liftToStream`.
 */
final object batch { //scalastyle:ignore 
  def liftToStream[A](prog: DBProg[A]): Stream[DBOps, Either[DBError, A]] = Stream.eval(prog.value)

  /**
   * Create all values in the Foldable F.
   */
  def createDocs[F[_]](foldable: F[(Key, RawJsonString)])(implicit F: Foldable[F]): Stream[DBOps, Either[DBError, DBValue]] =
    Stream.emits(foldable.toList).evalMap { case (key, json) => createDoc(key, json).value }
}
