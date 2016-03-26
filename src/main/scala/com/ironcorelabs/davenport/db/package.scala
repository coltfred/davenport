//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task

package object db {
  implicit val DBOpsMonad: Monad[DBOps] = Free.freeMonad[Coyoneda[DBOp, ?]]

  /** A Free Co-yoneda of [[DBOp]] classes */
  type DBOps[A] = Free.FreeC[DBOp, A]

  /**
   * The basic building block sent to the datastore.
   *
   *  Effectively this captures expected return type of `DBError \/ A` and a series of [[DBOp]] classes or functions combined together.
   *  When passed to an datastore such as `MemDatastore` or `CouchDatastore`, these are executed.
   */
  type DBProg[A] = EitherT[DBOps, DBError, A]

  /**
   * Convienent alias for the document type we return.
   */
  type DBValue = DBDocument[RawJsonString]

  //
  //Smart Constructors for DBOps
  //

  /** Return a document given some key */
  def getDoc(k: Key): DBOps[DBValue] = liftToFree(GetDoc(k))

  /** Create a document with the given key */
  def createDoc(k: Key, doc: RawJsonString): DBOps[DBValue] =
    liftToFree(CreateDoc(k, doc))

  /** Update a doc given its key, new value, and correct commitVersion */
  def updateDoc(k: Key, doc: RawJsonString, commitVersion: CommitVersion): DBOps[DBValue] =
    liftToFree(UpdateDoc(k, doc, commitVersion))

  /** Remove a doc from the DB given its key */
  def removeKey(k: Key): DBOps[Unit] = liftToFree(RemoveKey(k))

  /** Fetch a counter from the DB given a key */
  def getCounter(k: Key): DBOps[Long] = liftToFree(GetCounter(k))

  /** Increment a counter in the DB and return for some key and delta */
  def incrementCounter(k: Key, delta: Long = 1): DBOps[Long] =
    liftToFree(IncrementCounter(k, delta))

  def pure[A](a: => A): DBOps[A] = liftToFree(Pure(() => a))

  def attemptDBError[A](op: DBOps[A]): DBOps[DBError \/ A] = liftToFree(Attempt(op)).map(_.leftMap(DBError.fromThrowable))

  //
  // Common combinators for DBProg
  //

  /**
   * Convenience function to fetch a doc and transform it via a function `f`
   *
   *  In practice, this is more an example showing how to build a function like
   *  this. More commonly a higher level class that can be serialized to the db
   *  will have a modify function that transforms and calls this under the hood.
   */
  def modifyDoc(k: Key, f: RawJsonString => RawJsonString): DBOps[DBValue] = for {
    t <- getDoc(k)
    res <- updateDoc(k, f(t.data), t.commitVersion)
  } yield res

  /**
   * The `liftIntoDBProg` operations allow any function or value to be deferred to
   *  the executor.
   *
   *  This will most often be used when using for comprehensions mixing [[DBOp]]
   *  operations with other data extraction such as json de/serialization.
   */
  def liftIntoDBProg[A](opt: Option[A], dbError: DBError): DBProg[A] = EitherT.eitherT(Monad[DBOps].point(opt \/> dbError))
  def liftIntoDBProg[A](opt: Option[A], errormessage: String): DBProg[A] = liftIntoDBProg(opt, GeneralError(new Exception(errormessage)))
  def liftToDBProg[A](ops: DBOps[A]): DBProg[A] = EitherT.eitherT(attemptDBError(ops))
  def liftDisjunction[A](either: DBError \/ A): DBProg[A] = EitherT.eitherT(Monad[DBOps].point(either))
  private def liftToFree[A](a: DBOp[A]): DBOps[A] = Free.liftFC(a)
}
