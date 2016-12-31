//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import db._
import fs2.{ Task, Stream }
import datastore.Datastore

// The convention is for syntax objects to start with lower case, so they look
// like package names. Scalastyle doesn't care for this, so ignore the line.
final object dbprog extends DBProgOps // scalastyle:ignore

trait DBProgOps {
  implicit class OurDBProgOps[A](self: DBProg[A]) {
    def process: Stream[DBOps, Either[DBError, A]] = batch.liftToStream(self)
    def execute(d: Datastore): Task[Either[DBError, A]] = d.execute(self.value)
  }
}
