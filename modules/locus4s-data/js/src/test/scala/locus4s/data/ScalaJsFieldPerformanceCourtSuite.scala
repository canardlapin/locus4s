package locus4s.data

import locus4s.FiniteDomain
import locus4s.Index
import locus4s.SomeFiniteDomain
import locus4s.TotalMap
import scala.scalajs.js

final class ScalaJsFieldPerformanceCourtSuite extends munit.FunSuite:
  test("typed map and primitive field lookup retain primitive throughput"):
    val size = 2_000_000
    val packed = ephemeral("scalajs-field-lookup", size)
    val domain = packed.value
    val mapping = TotalMap.identity(domain)
    val values = Array.tabulate(size)(identity)
    val field =
      new Field[packed.S, Int]:
        val space: FiniteDomain[packed.S] =
          domain

        def apply(index: Index[packed.S]): Int =
          values(index.ordinal)

    var checksum = 0L
    val started = js.Date.now()
    domain.foreachIndex: index =>
      checksum += field(mapping(index)).toLong
    val elapsedMilliseconds = js.Date.now() - started

    assertEquals(checksum, size.toLong * (size.toLong - 1L) / 2L)
    assert(
      elapsedMilliseconds < 10_000.0,
      s"$size typed map/field reads took $elapsedMilliseconds ms"
    )

  private def ephemeral(
      name: String,
      size: Int
  ): SomeFiniteDomain =
    FiniteDomain.ephemeral(name, size) match
      case Right(domain) => domain
      case Left(error)   => fail(error.message)
