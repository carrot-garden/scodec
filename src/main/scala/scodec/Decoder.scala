package scodec

import scala.language.higherKinds

import scodec.bits.BitVector

import shapeless.Lazy

/**
 * Supports decoding a value of type `A` from a `BitVector`.
 *
 * @groupname primary Primary Members
 * @groupprio primary 0
 *
 * @groupname combinators Basic Combinators
 * @groupprio combinators 10
 *
 * @groupname coproduct Coproduct Support
 * @groupprio coproduct 13
 */
trait Decoder[+A] { self =>

  /**
   * Attempts to decode a value of type `A` from the specified bit vector.
   *
   * @param bits bits to decode
   * @return error if value could not be decoded or the remaining bits and the decoded value
   * @group primary
   */
  def decode(bits: BitVector): DecodeResult[A]

  /**
   * Converts this decoder to a `Decoder[B]` using the supplied `A => B`.
   * @group combinators
   */
  def map[B](f: A => B): Decoder[B] = new Decoder[B] {
    def decode(bits: BitVector) = self.decode(bits) map { a => f(a) }
  }

  /**
   * Converts this decoder to a `Decoder[B]` using the supplied `A => Decoder[B]`.
   * @group combinators
   */
  def flatMap[B](f: A => Decoder[B]): Decoder[B] = new Decoder[B] {
    def decode(bits: BitVector) = self.decode(bits) flatMapWithRemainder { (a, rem) => f(a).decode(rem) }
  }

  /**
   * Converts this decoder to a `Decoder[B]` using the supplied `A => Attempt[B]`.
   * @group combinators
   */
  def emap[B](f: A => Attempt[B]): Decoder[B] = new Decoder[B] {
    def decode(bits: BitVector) = self.decode(bits) flatMapWithRemainder { (a, rem) => DecodeResult.fromAttempt(f(a), rem) }
  }

  /**
   * Converts this decoder to a new decoder that fails decoding if there are remaining bits.
   * @group combinators
   */
  def complete: Decoder[A] = new Decoder[A] {
    def decode(bits: BitVector) = self.decode(bits) flatMapWithRemainder { (a, rem) =>
      if (rem.isEmpty) DecodeResult.successful(a, rem) else {
        DecodeResult.failure {
          val max = 512
          if (rem.sizeLessThan(max + 1)) {
            val preview = rem.take(max)
            Err(s"${preview.size} bits remaining: 0x${preview.toHex}")
          } else Err(s"more than $max bits remaining")
        }
      }
    }
  }

  /**
   * Gets this as a `Decoder`.
   * @group combinators
   */
  def asDecoder: Decoder[A] = this

  /**
   * Converts this to a codec that fails encoding with an error.
   * @group combinators
   */
  def decodeOnly[AA >: A]: Codec[AA] = new Codec[AA] {
    def encode(a: AA) = EncodeResult.failure(Err("encoding not supported"))
    def decode(bits: BitVector) = self.decode(bits)
  }
}

/**
 * Provides functions for working with decoders.
 *
 * @groupname conv Conveniences
 * @groupprio conv 2
 */
trait DecoderFunctions {

  /**
   * Decodes a tuple `(A, B)` by first decoding `A` and then using the remaining bits to decode `B`.
   * @group conv
   */
  final def decodeBoth[A, B](decA: Decoder[A], decB: Decoder[B])(buffer: BitVector): DecodeResult[(A, B)] =
    decodeBothCombine(decA, decB)(buffer) { (a, b) => (a, b) }

  /**
   * Decodes a `C` by first decoding `A` and then using the remaining bits to decode `B`, then applying the decoded values to the specified function to generate a `C`.
   * @group conv
   */
  final def decodeBothCombine[A, B, C](decA: Decoder[A], decB: Decoder[B])(buffer: BitVector)(f: (A, B) => C): DecodeResult[C] = {
    // Note: this could be written using DecodingContext but this function is called *a lot* and needs to be very fast
    decA.decode(buffer) match {
      case e @ DecodeResult.Failure(_) => e
      case DecodeResult.Successful(a, postA) =>
        decB.decode(postA) match {
          case e @ DecodeResult.Failure(_) => e
          case DecodeResult.Successful(b, rest) => DecodeResult.successful(f(a, b), rest)
        }
      }
  }

  /**
   * Repeatedly decodes values of type `A` from the specified vector and returns a collection of the specified type.
   * Terminates when no more bits are available in the vector or when `limit` is defined and that many records have been
   * decoded. Exits upon first decoding error.
   * @group conv
   */
  final def decodeCollect[F[_], A](dec: Decoder[A], limit: Option[Int])(buffer: BitVector)(implicit cbf: collection.generic.CanBuildFrom[F[A], A, F[A]]): DecodeResult[F[A]] = {
    val bldr = cbf()
    var remaining = buffer
    var count = 0
    var maxCount = limit getOrElse Int.MaxValue
    var error: Option[Err] = None
    while (count < maxCount && remaining.nonEmpty) {
      dec.decode(remaining) match {
        case DecodeResult.Successful(value, rest) =>
          bldr += value
          count += 1
          remaining = rest
        case DecodeResult.Failure(err) =>
          error = Some(err.pushContext(count.toString))
          remaining = BitVector.empty
      }
    }
    DecodeResult.fromErrOption(error, (bldr.result, remaining))
  }

  /**
   * Creates a decoder that decodes with each of the specified decoders, returning
   * the first successful result.
   * @group conv
   */
  final def choiceDecoder[A](decoders: Decoder[A]*): Decoder[A] = new Decoder[A] {
    def decode(buffer: BitVector): DecodeResult[A] = {
      @annotation.tailrec def go(rem: List[Decoder[A]], lastErr: Err): DecodeResult[A] = rem match {
        case Nil => DecodeResult.failure(lastErr)
        case hd :: tl =>
          hd.decode(buffer) match {
            case res: DecodeResult.Successful[A] => res
            case DecodeResult.Failure(err) => go(tl, err)
          }
      }
      go(decoders.toList, Err("no decoders provided"))
    }
  }
}

/**
 * Companion for [[Decoder]].
 *
 * @groupname ctor Constructors
 * @groupprio ctor 1
 *
 * @groupname inst Typeclass Instances
 * @groupprio inst 3
 */
object Decoder extends DecoderFunctions {

  /**
   * Provides syntax for summoning a `Decoder[A]` from implicit scope.
   * @group ctor
   */
  def apply[A](implicit dec: Decoder[A]): Decoder[A] = dec

  /**
   * Creates a decoder from the specified function.
   * @group ctor
   */
  def apply[A](f: BitVector => DecodeResult[A]): Decoder[A] = new Decoder[A] {
    def decode(bits: BitVector) = f(bits)
  }

  /**
   * Decodes the specified bit vector in to a value of type `A` using an implicitly available codec.
   * @group conv
   */
  def decode[A](bits: BitVector)(implicit d: Lazy[Decoder[A]]): DecodeResult[A] = d.value.decode(bits)

  /**
   * Creates a decoder that always decodes the specified value and returns the input bit vector unmodified.
   * @group ctor
   */
  def point[A](a: => A): Decoder[A] = new Decoder[A] {
    private lazy val value = a
    def decode(bits: BitVector) = DecodeResult.successful(value, bits)
    override def toString = s"const($value)"
  }
}
