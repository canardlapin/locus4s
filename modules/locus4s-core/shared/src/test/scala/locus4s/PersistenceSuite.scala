package locus4s

import munit.FunSuite

final class PersistenceSuite extends FunSuite:
  private val source = restored("persist-source", 5).space
  private val target = restored("persist-target", 3).space

  test("Domain restoration is idempotent and ignores label drift"):
    val firstRecord =
      mustRight(DomainRecord.parse("persist-domain", "first", 4))
    val renamed =
      mustRight(DomainRecord.parse("persist-domain", "renamed", 4))
    val first = mustRight(DomainRegistry.empty.restore(firstRecord))
    val second = mustRight(first.registry.restore(renamed))

    assert(first.space.sameRuntimeOwnerAs(second.space))
    assertEquals(second.registry.size, 1)

  test("Region empty whole and sparse records round-trip"):
    val regions =
      Vector(
        Region.empty(source),
        Region.whole(source),
        mustRight(Region.fromOrdinals(source, Vector(4, 1, 3)))
      )

    regions.foreach: region =>
      val record = mustRight(Persistence.record(region))
      assertEquals(
        mustRight(Persistence.restore(source, record)),
        region
      )

  test("Selection assigns and restores an explicit position identity"):
    val selection =
      mustRight(Selection.fromOrdinals(source, Vector(4, 1, 3)))
    assert(!selection.positions.isPersistable)
    assert(Persistence.record(selection).isLeft)
    val positionRecord =
      mustRight(
        DomainRecord.parse(
          "persist-selection",
          "selected positions",
          3
        )
      )
    val record =
      mustRight(Persistence.record(selection, positionRecord))
    val restoredSelection =
      mustRight(
        Persistence.restore(
          source,
          record,
          DomainRegistry.empty
        )
      )

    assertEquals(restoredSelection.selection, selection)
    assert(restoredSelection.selection.positions.isPersistable)
    assertEquals(
      restoredSelection.selection.positions.persistentKey,
      Some(positionRecord.key)
    )

  test("maps partial maps and relations round-trip"):
    val total =
      mustRight(
        TotalMap.fromTargetOrdinals(
          source,
          target,
          Vector(0, 1, 2, 1, 0)
        )
      )
    val partial =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          source,
          target,
          Vector(Some(0), None, Some(2), Some(1), None)
        )
      )
    val relation =
      mustRight(
        Relation.fromOrdinalRows(
          source,
          target,
          Vector(
            Vector(0, 1),
            Vector.empty,
            Vector(2),
            Vector(0, 2),
            Vector(1)
          )
        )
      )

    assertEquals(
      mustRight(
        Persistence.restore(
          source,
          target,
          mustRight(Persistence.record(total))
        )
      ),
      total
    )
    assertEquals(
      mustRight(
        Persistence.restore(
          source,
          target,
          mustRight(Persistence.record(partial))
        )
      ),
      partial
    )
    assertEquals(
      mustRight(
        Persistence.restore(
          source,
          target,
          mustRight(Persistence.record(relation))
        )
      ),
      relation
    )

  test("corrupt and conflicting records fail closed"):
    val wrongSource = restored("wrong-source", source.size).space
    val regionRecord =
      RegionRecord(
        source.key,
        RegionEncoding.SortedOrdinals(Vector(2, 1))
      )
    val relationRecord =
      RelationRecord(
        source.key,
        target.key,
        RelationEncoding.Csr(
          Vector(0, 2, 1, 1, 1, 1),
          Vector(0, 1)
        )
      )

    assert(
      Persistence.restore(wrongSource, regionRecord) match
        case Left(_: PersistenceError.DomainKeyMismatch) => true
        case _                                           => false
    )
    assertEquals(
      Persistence.restore(source, regionRecord),
      Left(
        PersistenceError.InvalidRegion(
          RegionError.NotStrictlyIncreasing(1, 2, 1)
        )
      )
    )
    assert(
      Persistence.restore(source, target, relationRecord) match
        case Left(_: PersistenceError.InvalidRelation) => true
        case _                                         => false
    )

  test("ephemeral endpoints cannot be persisted as spaces"):
    val ephemeral =
      mustRight(FiniteDomain.ephemeral("temporary", 2)).value
    val region = Region.whole(ephemeral)

    assert(
      Persistence.record(region) match
        case Left(_: PersistenceError.EphemeralDomain) => true
        case _                                         => false
    )

  private def restored(id: String, size: Int): DomainResolution =
    val record = mustRight(DomainRecord.parse(id, id, size))
    mustRight(DomainRegistry.empty.restore(record))

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
