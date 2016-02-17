//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package internal

import argonaut._, Argonaut._
// {
//         "elapsedTime": "168.122937ms",
//         "executionTime": "167.94446ms",
//         "resultCount": 1,
//         "resultSize": 210,
//         "sortCount": 1
//     }

final case class N1qlResponseInfo(elapsedTime: String, executionTime: String, resultCount: Int, resultSize: Int, sortCount: Int)

final object N1qlResponseInfo {
  implicit final val N1qlResponseInfoDecodeJson = DecodeJson { c =>
    for {
      elapsedTime <- (c --\ "elapsedTime").as[String]
      executionTime <- (c --\ "executionTime").as[String]
      resultCount <- (c --\ "resultCount").as[Int]
      resultSize <- (c --\ "resultSize").as[Int]
      sortCount <- (c --\ "sortCount").as[Int]
    } yield N1qlResponseInfo(elapsedTime, executionTime, resultCount, resultSize, sortCount)
  }

  implicit final val ByteVectorDecoder: codec.ByteVectorDecoder[N1qlResponseInfo] = codec.ByteVectorDecoder.fromDecodeJson(DecodeJson.of[N1qlResponseInfo])
}
