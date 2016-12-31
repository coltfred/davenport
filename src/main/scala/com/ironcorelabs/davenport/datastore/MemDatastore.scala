//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package datastore

import fs2.Task
import fs2.interop.cats._
import cats._
import cats.data._
import cats.arrow.FunctionK
import cats.implicits._
import db._

abstract class MemDatastore extends Datastore {
  import MemDatastore._
  protected var map: KVMap

  def execute: FunctionK[DBOps, Task] = Lambda[FunctionK[DBOps, Task]] { prog =>
    //Note that the Task.delay captures the current state when this op is run, which is important
    //if you rerun a Task.
    executeKVState(prog).modify { newMap =>
      map = newMap
      newMap
    }.runA(map)
    //   Task.delay(map).flatMap((_)).map {
    //     case (newM, value) =>
    //       //In order to provide the same semantics as Couch, once a value has been "computed" it will be 
    //       //committed to the DB. Note that this might overwrite someone elses changes in a multi-thread environment.
    //       map = newM
    //       value
    //   }
    // }
  }
}

object MemDatastore {
  /** Backend of the memory store is a Map from Key -> RawJsonString */
  type KVMap = Map[Key, RawJsonString]
  type KVState[A] = StateT[Task, KVMap, A]

  def apply(m: KVMap): MemDatastore = new MemDatastore {
    protected var map = m
  }

  def empty: MemDatastore = apply(Map.empty)

  val executeKVState: FunctionK[DBOps, KVState] = Lambda[FunctionK[DBOps, KVState]] {
    _.foldMap(toKVState)
  }

  /** Arbitrary implementation of the commitVersion for records in the DB */
  private[davenport] def genCommitVersion(s: RawJsonString): CommitVersion =
    CommitVersion(scala.util.hashing.MurmurHash3.stringHash(s.value).toLong)

  private def modifyState(s: KVMap): (KVMap, Either[DBError, Unit]) = s -> Either.right(())
  private def modifyStateDbv(s: KVMap, dbv: DBValue): (KVMap, Either[DBError, DBValue]) = s -> Either.right(dbv)
  private def notFoundError[A](key: Key): Either[DBError, A] = Either.left(ValueNotFound(key))
  //Convienience method to lift f into KVState.
  private def state[A](f: KVMap => (KVMap, A)): KVState[A] = StateT[Task, KVMap, A] { map =>
    Task.delay(f(map))
  }

  private def getDoc(k: Key): KVState[Either[DBError, DBValue]] = {
    state { m: KVMap =>
      m.get(k).map(json => m -> Either.right(DBDocument(k, genCommitVersion(json), json)))
        .getOrElse(m -> notFoundError[DBValue](k))
    }
  }

  private def updateDoc(k: Key, doc: RawJsonString, commitVersion: CommitVersion): KVState[Either[DBError, DBValue]] = {
    state { m: KVMap =>
      m.get(k).map { json =>
        val storedCommitVersion = genCommitVersion(json)
        if (commitVersion == storedCommitVersion) {
          modifyStateDbv(m + (k -> doc), DBDocument(k, genCommitVersion(doc), doc))
        } else {
          m -> Either.left(CommitVersionMismatch(k))
        }
      }.getOrElse(m -> notFoundError(k))
    }
  }

  private def getCounter(k: Key): KVState[Either[DBError, Long]] = {
    state { m: KVMap =>
      m.get(k).map { json =>
        m -> Either.catchNonFatal(json.value.toLong).leftMap(GeneralError(_))
      } getOrElse {
        (m + (k -> RawJsonString("0")) -> Either.right(0L))
      }
    }
  }

  private def incrementCounter(k: Key, delta: Long): KVState[Either[DBError, Long]] = {
    state { m: KVMap =>
      m.get(k).map { json =>
        // convert to long and increment by delta
        val newval = Either.catchNonFatal(json.value.toLong + delta).leftMap(GeneralError(_))
        val newMap = newval.fold(_ => m, v => m + (k -> RawJsonString(v.toString)))
        newMap -> newval
      }.getOrElse {
        // save delta to db
        (m + (k -> RawJsonString(delta.toString)), Either.right(delta))
      }
    }
  }

  private def toKVState: FunctionK[DBOp, KVState] = Lambda[FunctionK[DBOp, KVState]] {
    case GetDoc(k: Key) => getDoc(k)
    case UpdateDoc(k, doc, commitVersion) => updateDoc(k, doc, commitVersion)
    case CreateDoc(k, doc) => state { m: KVMap =>
      m.get(k).map(_ => m -> Either.left(ValueExists(k))).
        getOrElse(modifyStateDbv(m + (k -> doc), DBDocument(k, genCommitVersion(doc), doc)))
    }
    case RemoveKey(k) => state { m: KVMap =>
      val keyOrError = m.get(k).map(_ => Either.right(k)).getOrElse(Either.right(ValueNotFound(k)))
      keyOrError.fold(t => m -> Either.left(t), key => modifyState(m - k))
    }
    case GetCounter(k) => getCounter(k)
    case IncrementCounter(k, delta) => incrementCounter(k, delta)
  }
}
