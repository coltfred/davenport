//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package internal

import db._

import argonaut._, Argonaut._ //Bring in argonaut syntax.

case class N1qlQuery(field: String, value: String, op: Comparison, limit: Int, offset: Int, consistency: ScanConsistency) {
  import N1qlQuery._

  private[internal] val toPositionalStatement: String => String = { bucketName =>
    s"SELECT $RecordString, meta($RecordString) as $MetaString FROM $bucketName $RecordString where ${opToFunc(op)(field)} order by $MetaString.id limit $limit offset $offset ;"
  }

}

object N1qlQuery {

  def selectRecordForId(id: String, op: Comparison, limit: Int, offset: Int, consistency: ScanConsistency): N1qlQuery =
    N1qlQuery(s"meta(${N1qlQuery.RecordString}).id", id, op, limit, offset, consistency)

  def createRequestCodec(bucketName: String): EncodeJson[N1qlQuery] =
    EncodeJson { (query: N1qlQuery) =>
      ("statement" := query.toPositionalStatement(bucketName)) ->:
        ("args" := List(query.value)) ->:
        ("scan_consistency" := consistencyToJsonString(query.consistency)) ->:
        jEmptyObject
    }

  def consistencyToJsonString(consistency: ScanConsistency): String = consistency match {
    case AllowStale => "not_bounded"
    case EnsureConsistency => "request_plus"
  }

  final val MetaString = "meta"
  final val RecordString = "record"
  //These are constants that match couchbase fields
  final val IdString = "id"
  final val CasString = "cas"
  final val TypeString = "type"
  //COLT: This probably doesn't really belong here.
  def jsonToDBDocument(json: Json): Option[DBValue] = for {
    metaJson <- json.field(MetaString)
    (key, cas, typ) <- extractFromMeta(metaJson)
    record <- json.field(RecordString)
    value <- extractFromRecord(typ, record)
  } yield DBDocument(Key(key), CommitVersion(cas), RawJsonString(value))

  private def extractFromRecord(typ: String, json: Json): Option[String] = typ match {
    case "json" =>
      Some(json.nospaces)
    case typ =>
      //logger.warn(s"We only know how to decode json, but we were asked to decode $typ. Threw '${json.nospaces}' out.")
      None
  }

  /**
   * Extract the key, cas and type of record from the meta Json
   */
  private def extractFromMeta(meta: Json): Option[(String, Long, String)] =
    for {
      keyJson <- meta.field(IdString)
      key <- keyJson.string
      casJson <- meta.field(CasString)
      casJNumber <- casJson.number
      cas <- casJNumber.toLong
      typJson <- meta.field(TypeString)
      typ <- typJson.string
    } yield (key, cas, typ)

  private def opToFunc(op: Comparison)(name: String): String = {
    val string = op match {
      case EQ => "="
      case GT => ">"
      case LT => "<"
      case LTE => "<="
      case GTE => ">="
    }
    name + string + "$1"
  }
}
