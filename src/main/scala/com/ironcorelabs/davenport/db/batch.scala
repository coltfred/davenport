//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport.db
import scalaz.stream.Process
import scalaz.{ \/, Foldable }
import scalaz.syntax.foldable._

/**
 * Object that contains batch operations for DB. They have been separated out because they cannot be mixed with
 * `DBProg` operations without first lifting them into a process via `liftToProcess`.
 */
final object batch { //scalastyle:ignore 
  def liftToProcess[A](prog: DBProg[A]): Process[DBOps, DBError \/ A] = Process.eval(prog.run)
  def liftToProcess[A](prog: DBOps[A]): Process[DBOps, A] = Process.eval(prog)

  /**
   * Create all values in the Foldable F.
   */
  def createDocs[F[_]](foldable: F[(Key, RawJsonString)])(implicit F: Foldable[F]): Process[DBOps, DBValue] =
    Process.emitAll(foldable.toList).evalMap { case (key, json) => createDoc(key, json) }

  def createDocsWithAttempt[F[_]](foldable: F[(Key, RawJsonString)])(implicit F: Foldable[F]): Process[DBOps, DBError \/ DBValue] =
    Process.emitAll(foldable.toList).evalMap { case (key, json) => attemptDBError(createDoc(key, json)) }

}
