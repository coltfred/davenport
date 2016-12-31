//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport.db

import argonaut._, Argonaut._
import cats._
import cats.data._
import cats.implicits._

/**
 * A document that's either destined to be put into the DB or came out of the DB.
 * Key - The key where the document is stored.
 * commitVersion - The CommitVersion of the current document
 * data - The data stored in the document, typically RawJsonString when it comes out of the DB.
 */
final case class DBDocument[A](key: Key, commitVersion: CommitVersion, data: A) {
  def map[B](f: A => B): DBDocument[B] = DBDocument(key, commitVersion, f(data))
}

final object DBDocument {
  import cats._
  import cats.data._
  import cats.arrow.FunctionK
  import cats.implicits._
  implicit val instance: Functor[DBDocument] = new Functor[DBDocument] {
    def map[A, B](fa: DBDocument[A])(f: A => B): DBDocument[B] = fa.map(f)
  }

  // implicit def jsonDecode[A](implicit decodeA: DecodeJson[A]): DecodeJson[DBDocument[A]] = for {
  //   a <- decodeA
  //   key <- DecodeJson[String]
  // } yield DBDocument(Key(key), Comm)

  implicit def dbDocumentEqual[A](implicit aEq: Eq[A]): Eq[DBDocument[A]] = Eq.instance[DBDocument[A]] {
    (doc1, doc2) =>
      (doc1, doc2) match {
        case (DBDocument(key1, commitVersion1, a1), DBDocument(key2, commitVersion2, a2)) =>
          aEq.eqv(a1, a2) && Eq[String].eqv(key1.value, key2.value) &&
            Eq[Long].eqv(commitVersion1.value, commitVersion2.value)
      }
  }

  /**
   * Create a document out of `t` using `codec` and store it at `key`.
   */
  def create[T](key: Key, t: T)(implicit codec: EncodeJson[T]): DBProg[DBDocument[T]] =
    createDoc(key, RawJsonString(t.asJson.nospaces)).map(_.map(_ => t))

  /**
   * Fetch a document from the datastore and decode it using `codec`
   * If deserialization fails the DBProg will result in a left disjunction.
   */
  def get[T](k: Key)(implicit codec: DecodeJson[T]): DBProg[DBDocument[T]] = for {
    s <- getDoc(k)
    decodedValue = s.data.value.decodeWithMessage({ t: T => Either.right(t) }, { s: String => Either.left(DeserializationError(k, s)) })
    v <- liftDisjunction(decodedValue)
  } yield DBDocument(k, s.commitVersion, v)

  /**
   * A short way to get and update by running the data through `f`.
   */
  def modify[T](k: Key, f: T => T)(implicit codec: CodecJson[T]): DBProg[DBDocument[T]] = for {
    v <- get(k)(codec)
    result <- update(v.map(f))
  } yield result

  /**
   * Update the document to a new value.
   */
  def update[T](doc: DBDocument[T])(implicit codec: EncodeJson[T]): DBProg[DBDocument[T]] =
    updateDoc(doc.key, RawJsonString(doc.data.asJson.toString), doc.commitVersion).
      map(newDoc => newDoc.map(_ => doc.data))

  /**
   * Remove the document stored at key `key`
   */
  def remove(key: Key): DBProg[Unit] = removeKey(key)
}
