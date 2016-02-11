package com.ironcorelabs.davenport
package error

import com.couchbase.client.core.CouchbaseException
import codec.DecodeError
sealed trait CouchbaseError extends CouchbaseException
//Splitting these ADT entries didn't work well.
// scalastyle:off line.size.limit
final case class InvalidPasswordException(bucketName: String) extends CouchbaseException(s"Invalid password for bucket '$bucketName'") with CouchbaseError
final case class DocumentDoesNotExistException(documentKey: String, bucketName: String) extends CouchbaseException(s"Document '$documentKey' in '$bucketName' does not exist.") with CouchbaseError
final case class BucketDoesNotExistException(bucketName: String) extends CouchbaseException(s"'$bucketName' does not exist.") with CouchbaseError
final case class DocumentAlreadyExistsException(id: String) extends CouchbaseException(s"Document with id '$id' already exists.") with CouchbaseError
final case class CouchbaseOutOfMemoryException() extends CouchbaseException("Couchbase is out of memory.") with CouchbaseError
final case class CASMismatchException(id: String) extends CouchbaseException(s"The passed in CAS for '$id' didn't match the expected.") with CouchbaseError
final case class RequestTooBigException() extends CouchbaseException("The request was too big.") with CouchbaseError
final case class TemporaryFailureException() extends CouchbaseException("The couchbase cluster had a transient error. Try your request again.") with CouchbaseError
final case class GenericException(message: String) extends CouchbaseException(message) with CouchbaseError
// scalastyle:on line.size.limit

//For the following I need to defer which constructor to call in the base class which I cannot do using the normal syntax.
//The following was taken from Seth's answer on SO: http://stackoverflow.com/a/3299832/1226945
trait DocumentDecodeFailedException extends CouchbaseException with CouchbaseError
object DocumentDecodeFailedException {
  def apply(cause: DecodeError): DocumentDecodeFailedException = cause match {
    case DecodeError(message, None) => new CouchbaseException(message) with DocumentDecodeFailedException
    case DecodeError(message, Some(ex)) => new CouchbaseException(message, ex) with DocumentDecodeFailedException
  }
}
