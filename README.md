scodec
======

Scala combinator library for working with binary data.

Design Constraints
------------------

This library focuses on contract-first and pure functional encoding and decoding of binary data.
The following design constraints are considered:
 - Binary structure should mirror protocol definitions and be self-evident under casual reading
 - Mapping binary structures to types should be statically verified
 - Encoding and decoding should be purely functional
 - Failures in encoding and decoding should provide descriptive errors
 - Compiler plugin should not be used

As a result, the library is implemented as a combinator based DSL.
Performance is considered but yields to the above design constraints.

Acknowledgements
----------------
The library uses [Shapeless](https://github.com/milessabin/shapeless)
and is heavily influenced by scala.util.parsing.combinator.

Administrative
--------------

This project is licensed under a [3-clause BSD license](LICENSE).

The [Typelevel mailing list](https://groups.google.com/forum/#!forum/typelevel) contains release announcements and is generally a good place to go for help. Also consider using the [scodec tag on StackOverflow](http://stackoverflow.com/questions/tagged/scodec).

People are expected to follow the [Typelevel Code of Conduct](http://typelevel.org/conduct.html)
when discussing scodec on the Github page, IRC channel, mailing list,
or other venues.

Concerns or issues can be sent to Michael Pilquist (*mpilquist@gmail.com*) or
to [Typelevel](http://typelevel.org/about.html).

Introduction
------------

The primary abstraction is a [`Codec[A]`](src/main/scala/scodec/Codec.scala), which supports encoding a value of type `A` to a
`BitVector` and decoding a `BitVector` to a value of type `A`.

The [`codecs`](src/main/scala/scodec/codecs/package.scala) package provides a number of predefined codecs and combinators.

```scala
    import scodec._
    import scodec.bits._
    import codecs._
    import scalaz.\/

    // Create a codec for an 8-bit unsigned int followed by an 8-bit unsigned int followed by a 16-bit unsigned int
    val firstCodec = (uint8 ~ uint8 ~ uint16)

    // Decode a bit vector using that codec
    val result: DecodeResult[(Int ~ Int ~ Int)] = Codec.decode(firstCodec, BitVector(0x10, 0x2a, 0x03, 0xff))

    // Sum the result
    val add3 = (_: Int) + (_: Int) + (_: Int)
    val sum: DecodeResult[Int] = result map add3
```

Automatic case class binding is supported via Shapeless HLists:

```scala
    case class Point(x: Int, y: Int, z: Int)

    val pointCodec = (int8 :: int8 :: int8).as[Point]

    val encoded: Attempt[BitVector] = pointCodec.encode(Point(-5, 10, 1))
    // Successful(BitVector(24 bits, 0xfb0a01))

    val decoded: Attempt[DecodeResult[Point]] = pointCodec.decode(hex"0xfb0a01".bits)
    // Successful(DecodeResult(Point(-5,10,1),BitVector(empty)))
```

Codecs can also be implicitly resolved, resulting in usage like:

```scala
    // Assuming Codec[Point] is in implicit scope

    val encoded: Attempt[BitVector] = Codec.encode(Point(-5, 10, 1))
    // Successful(BitVector(24 bits, 0xfb0a01))

    val decoded: Attempt[DecodeResult[Point]] = Codec.decode[Point](hex"0xfb0a01".bits)
    // Successful(DecodeResult(Point(-5,10,1),BitVector(empty)))
```

New codecs can be created by either implementing the `Codec` trait or by passing an encoder function and decoder function to the `Codec` apply method. Typically, new codecs are created by applying one or more combinators to existing codecs.

See [the guide](https://scodec.github.io/) for detailed documentation. Also, see [ScalaDoc](http://scodec.github.io/scodec/latest/api/). Especially:
 - [`Codec`](http://scodec.github.io/scodec/latest/api/scodec/Codec.html)
 - [`codecs` package](http://scodec.github.io/scodec/latest/api/index.html#scodec.codecs.package)

Examples
--------

There are various examples in the test directory, including codecs for:

 - [UDP Datagrams](src/test/scala/scodec/examples/UdpDatagramExample.scala)
 - [MPEG Packets](src/test/scala/scodec/examples/MpegPacketExample.scala)
 - [libpcap Files](src/test/scala/scodec/examples/PcapExample.scala)

The [scodec-protocols](https://github.com/scodec/scodec-protocols) has production
quality codecs for the above examples.

The [bitcoin-scodec](https://github.com/yzernik/bitcoin-scodec) library has a codec
for the Bitcoin network protocol.

The [scodec-msgpack](https://github.com/pocketberserker/scodec-msgpack) library provides
codecs for [MessagePack](http://msgpack.org/).

Getting Binaries
----------------

See the [releases page on the wiki](https://github.com/scodec/scodec/wiki/Releases).
