//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package datastore

import fs2.{ Task, Stream }
import cats.arrow.FunctionK
import fs2.interop.cats.reverse._
import db._

trait Datastore {
  //Define how you map each op to a Task using a Natural Transformation.
  def execute: FunctionK[DBOps, Task]

  def execute[A](db: DBProg[A]): Task[Either[DBError, A]] = execute(db.value)
  def executeP[A](p: Stream[DBOps, A]): Stream[Task, A] = p.translate(functionKToUf1(execute))
}
