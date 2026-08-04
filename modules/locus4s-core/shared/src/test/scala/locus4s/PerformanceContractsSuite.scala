package locus4s

final class PerformanceContractsSuite extends munit.FunSuite:
  test("sparse relation work is independent of enormous target size"):
    val source = ephemeral("sparse-source", 2)
    val middle = ephemeral("sparse-middle", 3)
    val target = ephemeral("sparse-target", 1_000_000_000)

    val first =
      mustRight(
        Relation.fromCsr(
          source,
          middle,
          Vector(0, 3, 3),
          Vector(0, 1, 2)
        )
      )
    val second =
      mustRight(
        Relation.fromCsr(
          middle,
          target,
          Vector(0, 2, 4, 6),
          Vector(
            1, 999_999_999, 1, 500_000_000, 7, 999_999_999
          )
        )
      )

    val sourceZero = mustRight(source.index(0))
    var visited = 0
    second.foreachTarget(mustRight(middle.index(0))): _ =>
      visited += 1
    assertEquals(visited, 2)

    val row = second.row(mustRight(middle.index(1)))
    assertEquals(row.cardinality, 2)
    assert(row.contains(mustRight(target.index(1))))
    assert(row.contains(mustRight(target.index(500_000_000))))

    val composed = first.andThen(second)
    assertEquals(composed.pairCount, 4)
    assertEquals(
      composed.row(sourceZero).ordinalsInDomainOrder.toVector,
      Vector(1, 7, 500_000_000, 999_999_999)
    )

  test("empty and whole structures remain compact at Int.MaxValue"):
    val huge = ephemeral("maximum-domain", Int.MaxValue)
    val empty = Region.empty(huge)
    val whole = Region.whole(huge)
    val relation = Relation.empty(huge, huge)

    assert(empty.isEmpty)
    assert(whole.isWhole)
    assertEquals(empty.complement, whole)
    assertEquals(whole.complement, empty)
    assert(relation.isEmpty)
    assertEquals(relation.csr.rowOffsets.length, 0)
    assertEquals(relation.csr.targets.length, 0)
    assertEquals(
      Relation.fromCsr(
        huge,
        huge,
        Vector(0),
        Vector(0)
      ),
      Left(RelationError.RowOffsetCountOverflow(Int.MaxValue))
    )

  test("fiber materialization handles the CSR row-offset limit explicitly"):
    val source = ephemeral("fiber-limit-source", 2)
    val hugeTarget = ephemeral("fiber-limit-target", Int.MaxValue)
    val emptyMapping =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          source,
          hugeTarget,
          Vector(None, None)
        )
      )
    val definedMapping =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          source,
          hugeTarget,
          Vector(Some(1), None)
        )
      )

    assert(mustRight(emptyMapping.fibers).isEmpty)
    assertEquals(
      definedMapping.fibers,
      Left(RelationError.RowOffsetCountOverflow(Int.MaxValue))
    )

  test("zero-cost index traversal preserves ordinal parity"):
    val size = 2_000_000
    val space = ephemeral("traversal-court", size)
    var checksum = 0L
    var count = 0

    space.foreachIndex: index =>
      checksum += index.ordinal.toLong
      count += 1

    assertEquals(count, size)
    assertEquals(checksum, size.toLong * (size.toLong - 1L) / 2L)

  private def ephemeral(name: String, size: Int): FiniteDomain[?] =
    mustRight(FiniteDomain.ephemeral(name, size)).value

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
