//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package datastore

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz._, Scalaz._
import db._

abstract class MemDatastore extends Datastore {
  import MemDatastore._
  protected var map: KVMap

  def execute: (DBOps ~> Task) = new (DBOps ~> Task) {
    def apply[A](prog: DBOps[A]): Task[A] = {
      //Note that the Task.delay captures the current state when this op is run, which is important
      //if you rerun a Task.
      Task.delay(map).flatMap(executeKVState(prog)(_)).map {
        case (newM, value) =>
          //In order to provide the same semantics as Couch, once a value has been "computed" it will be 
          //committed to the DB. Note that this might overwrite someone elses changes in a multi-thread environment.
          map = newM
          value
      }
    }
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

  val executeKVState: DBOps ~> KVState = new (DBOps ~> KVState) {
    def apply[A](db: DBOps[A]): KVState[A] = {
      Free.runFC[DBOp, KVState, A](db)(toKVState)
    }
  }

  /** Arbitrary implementation of the commitVersion for records in the DB */
  private[davenport] def genCommitVersion(s: RawJsonString): CommitVersion =
    CommitVersion(scala.util.hashing.MurmurHash3.stringHash(s.value).toLong)

  private def modifyStateDbv(s: KVMap, dbv: DBValue): (KVMap, DBValue) = s -> dbv
  //Convienience method to lift f into KVState.
  private def state[A](f: KVMap => (KVMap, A)): KVState[A] = StateT[Task, KVMap, A] { map =>
    Task.delay(f(map))
  }

  private def getDoc(k: Key): KVState[DBValue] = {
    state { m: KVMap =>
      m.get(k).map(json => m -> DBDocument(k, genCommitVersion(json), json))
        .getOrElse(throw error.DocumentDoesNotExistException(k.value, "Memory"))
    }
  }

  private def updateDoc(k: Key, doc: RawJsonString, commitVersion: CommitVersion): KVState[DBValue] = {
    state { m: KVMap =>
      m.get(k).map { json =>
        val storedCommitVersion = genCommitVersion(json)
        if (commitVersion == storedCommitVersion) {
          modifyStateDbv(m + (k -> doc), DBDocument(k, genCommitVersion(doc), doc))
        } else {
          throw error.CASMismatchException(k.value)
        }
      }.getOrElse(throw error.DocumentDoesNotExistException(k.value, "Memory"))
    }
  }

  private def getCounter(k: Key): KVState[Long] = {
    state { m: KVMap =>
      m.get(k).map { json =>
        m -> json.value.toLong
      } getOrElse {
        (m + (k -> RawJsonString("0")) -> 0L)
      }
    }
  }

  private def incrementCounter(k: Key, delta: Long): KVState[Long] = {
    state { m: KVMap =>
      m.get(k).map { json =>
        // convert to long and increment by delta
        val newval = json.value.toLong + delta
        val newMap = m + (k -> RawJsonString(newval.toString))
        newMap -> newval
      }.getOrElse {
        // save delta to db
        (m + (k -> RawJsonString(delta.toString)), delta)
      }
    }
  }

  private def toKVState: DBOp ~> KVState = new (DBOp ~> KVState) {
    def apply[A](op: DBOp[A]): KVState[A] = {
      op match {
        case GetDoc(k) => getDoc(k)
        case UpdateDoc(k, doc, commitVersion) => updateDoc(k, doc, commitVersion)
        case CreateDoc(k, doc) => state { m: KVMap =>
          m.get(k) match {
            case Some(_) => throw new error.DocumentAlreadyExistsException(k.value)
            case None => modifyStateDbv(m + (k -> doc), DBDocument(k, genCommitVersion(doc), doc))
          }
        }
        case RemoveKey(k) => state { m: KVMap =>
          val key = m.get(k).map(_ => k).getOrElse(throw new error.DocumentDoesNotExistException(k.value, "Memory"))
          ((m - k), ())
        }
        case GetCounter(k) => getCounter(k)
        case IncrementCounter(k, delta) => incrementCounter(k, delta)
        case Attempt(op) => StateT[Task, KVMap, A] { map =>
          executeKVState(op).run(map).attempt.map {
            case \/-((newState, value)) => (newState, value.right)
            case -\/(throwable) => (map, throwable.left)
          }
        }
        case Pure(a) => a().point[KVState]
      }
    }
  }
}
