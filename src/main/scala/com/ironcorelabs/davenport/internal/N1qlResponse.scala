//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package internal

import scodec.bits.ByteVector

case class N1qlResponse[A](info: Option[N1qlResponseInfo], signature: Option[N1qlSignature], errors: List[ByteVector], values: List[A]) {
  def map[B](f: A => B): N1qlResponse[B] = N1qlResponse(info, signature, errors, values.map(f))
}
