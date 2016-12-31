//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import fs2.{ Task, Stream }
import db.DBOps
import datastore.Datastore

// The convention is for syntax objects to start with lower case, so they look
// like package names. Scalastyle doesn't care for this, so ignore the line.
final object stream extends StreamOps // scalastyle:ignore

trait StreamOps {
  implicit class OurStreamOps[M[_], A](self: Stream[M, A]) {
    def execute(d: Datastore)(implicit ev: Stream[M, A] =:= Stream[DBOps, A]): Stream[Task, A] =
      d.executeP(self)
  }
}
