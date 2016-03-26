//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport.db

import scalaz._, Scalaz._
import scalaz.concurrent.Task

/** Any database operation must be represented by a `DBOp` */
sealed trait DBOp[A]
final case class GetDoc(key: Key) extends DBOp[DBValue]
final case class CreateDoc(key: Key, doc: RawJsonString) extends DBOp[DBValue]
final case class UpdateDoc(key: Key, doc: RawJsonString, commitVersion: CommitVersion) extends DBOp[DBValue]
final case class RemoveKey(key: Key) extends DBOp[Unit]
final case class GetCounter(key: Key) extends DBOp[Long]
final case class IncrementCounter(key: Key, delta: Long = 1) extends DBOp[Long]
final case class Attempt[A](op: DBOps[A]) extends DBOp[Throwable \/ A]
final case class Pure[A](a: () => A) extends DBOp[A]

object DBOp {
  implicit val catchable: Catchable[DBOps] = new Catchable[DBOps] {
    def attempt[A](ops: DBOps[A]): DBOps[Throwable \/ A] = Free.liftFC[DBOp, Throwable \/ A](Attempt(ops))
    def fail[A](err: Throwable): DBOps[A] = pure(throw err)
  }
}
