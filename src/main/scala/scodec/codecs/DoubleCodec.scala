package scodec
package codecs

import java.nio.ByteBuffer

import scodec.bits.{ BitVector, ByteOrdering }

private[codecs] final class DoubleCodec(ordering: ByteOrdering) extends Codec[Double] {

  private val byteOrder = ordering.toJava

  override def encode(value: Double) = {
    val buffer = ByteBuffer.allocate(8).order(byteOrder).putDouble(value)
    buffer.flip()
    EncodeResult.successful(BitVector.view(buffer))
  }

  override def decode(buffer: BitVector) =
    buffer.acquire(64) match {
      case Left(e) => DecodeResult.failure(Err.insufficientBits(64, buffer.size))
      case Right(b) => DecodeResult.successful(ByteBuffer.wrap(b.toByteArray).order(byteOrder).getDouble, buffer.drop(64))
    }

  override def toString = "double"
}

