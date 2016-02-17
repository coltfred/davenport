//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package internal

import codec.ByteVectorDecoder
import argonaut._, Argonaut._

// {
//         "meta": "object",
//         "record": "json"
//     }

case class N1qlSignature(m: Map[String, String])

object N1qlSignature {
  implicit def byteVectorDecoder(implicit jsonDecoder: ByteVectorDecoder[Json]) = ByteVectorDecoder.fromDecodeJson(DecodeJson.of[Map[String, String]]).map(N1qlSignature(_))
}
