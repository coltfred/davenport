//
// com.ironcorelabs.davenport.CouchDatastore
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package datastore

import scalaz._, Scalaz._
import scalaz.concurrent.Task
import db._

import internal.Bucket

/**
 * Create a CouchDatastore which operates on the bucket provided. Note that the primary way this should be used is through
 * [[CouchConnection.openDatastore]].
 */
final case class CouchDatastore(bucket: Task[Bucket]) extends Datastore {
  import CouchDatastore._
  /**
   * A function from DBOps[A] => Task[A] which operates on the bucket provided.
   */
  def execute: (DBOps ~> Task) = new (DBOps ~> Task) {
    def apply[A](prog: DBOps[A]): Task[A] = bucket.flatMap(executeK(prog).run(_))
  }
}

/**
 * Things related to translating DBOp to Kleisli[Task, Bucket, A] and some helpers for translating to Task[A].
 *
 * This object contains couchbase specific things such as CAS, which is modeled as CommitVersion.
 *
 * For details about how this translation is done, look at couchRunner which routes each DBOp to its couchbase
 * counterpart.
 */
final object CouchDatastore {
  type CouchK[A] = Kleisli[Task, Bucket, A]

  /**
   * Interpret the program into a Kleisli that will take a Bucket as its argument. Useful if you want to do
   * Kleisli arrow composition before running it.
   */
  // def executeK[A](prog: DBProg[A]): CouchK[DBError \/ A] = executeK(prog.run)

  /**
   * Basic building block. Turns the DbOps into a Kleisli which takes a Bucket, used by execute above.
   */
  def executeK[A](prog: DBOps[A]): CouchK[A] = Free.runFC[DBOp, CouchK, A](prog)(couchRunner)

  /**
   * In this case, the couchRunner object transforms [[DB.DBOp]] to
   * `scalaz.concurrent.Task`.
   * The only public method, apply, is what gets called as the grammar
   * is executed, calling it to transform [[DB.DBOps]] to functions.
   */
  private val couchRunner = new (DBOp ~> CouchK) {
    def apply[A](dbp: DBOp[A]): CouchK[A] = dbp match {
      case GetDoc(k) => getDoc(k)
      case CreateDoc(k, v) => createDoc(k, v)
      case GetCounter(k) => getCounter(k)
      case IncrementCounter(k, delta) => incrementCounter(k, delta)
      case RemoveKey(k) => removeKey(k)
      case UpdateDoc(k, v, cv) => updateDoc(k, v, cv)
      case Attempt(op) => executeK(op).attempt
      case Pure(a) =>
        val x = a()
        println(s"COLTCOLTCOLT $x")
        x.point[CouchK]
    }

    /*
     * Helpers for the datastore
     */
    private def getDoc(k: Key): CouchK[DBValue] = bucketToA(k)(_.get[RawJsonString](k))
    private def createDoc(k: Key, v: RawJsonString): CouchK[DBValue] = bucketToA(k)(_.create(k, v))
    private def removeKey(k: Key): CouchK[Unit] = bucketToA(k)(_.remove(k).map(_ => ()))
    private def getCounter(k: Key): CouchK[Long] = bucketToA(k)(_.getCounter(k).map(_.data))
    private def incrementCounter(k: Key, delta: Long): CouchK[Long] =
      bucketToA(k)(_.incrementCounter(k, delta).map(_.data))
    private def updateDoc(k: Key, v: RawJsonString, cv: CommitVersion): CouchK[DBValue] =
      bucketToA(k) { _.update(k, v, cv.value) }

    private def bucketToA[A, B](key: Key)(fetchOp: Bucket => Task[A]): Kleisli[Task, Bucket, A] =
      Kleisli.kleisli { bucket: Bucket => fetchOp(bucket) }
  }
}
