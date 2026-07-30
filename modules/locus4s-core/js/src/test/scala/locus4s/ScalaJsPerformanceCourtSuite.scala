package locus4s

import scala.scalajs.js

final class ScalaJsPerformanceCourtSuite extends munit.FunSuite:
  test("optimized-compatible traversal retains primitive ordinal throughput"):
    val size = 2_000_000
    val space = ephemeral("scalajs-traversal", size)
    var checksum = 0L

    val started = js.Date.now()
    space.foreachIndex: index =>
      checksum += index.ordinal.toLong
    val elapsedMilliseconds = js.Date.now() - started

    assertEquals(checksum, size.toLong * (size.toLong - 1L) / 2L)
    assert(
      elapsedMilliseconds < 10_000.0,
      s"$size primitive index visits took $elapsedMilliseconds ms"
    )

  test("sparse row retrieval does not scale with target-domain cardinality"):
    val source = ephemeral("scalajs-row-source", 1)
    val target = ephemeral("scalajs-row-target", 1_000_000_000)
    val relation =
      mustRight(
        Relation.fromCsr(
          source,
          target,
          Vector(0, 3),
          Vector(1, 500_000_000, 999_999_999)
        )
      )

    val started = js.Date.now()
    val row = relation.row(mustRight(source.index(0)))
    val elapsedMilliseconds = js.Date.now() - started

    assertEquals(row.cardinality, 3)
    assert(
      elapsedMilliseconds < 10_000.0,
      s"degree-3 row retrieval took $elapsedMilliseconds ms"
    )

  private def ephemeral(
      name: String,
      size: Int
  ): FiniteDomain[?] =
    mustRight(FiniteDomain.ephemeral(name, size)).value

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
