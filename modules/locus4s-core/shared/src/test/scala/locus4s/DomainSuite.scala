package locus4s

import munit.FunSuite

final class DomainSuite extends FunSuite:
  test("same-name fresh domains have distinct persistent and runtime identities"):
    val registry = sequentialRegistry("fresh-cortex")
    val first = mustRight(registry.fresh("cortex", 4))
    val second = mustRight(first.registry.fresh("cortex", 4))

    assertNotEquals(first.space.id, second.space.id)
    assert(!first.space.sameRuntimeOwnerAs(second.space))
    assert(!first.space.samePersistentIdentityAs(second.space))
    assertEquals(second.registry.size, 2)

  test("equal ordinals in distinct domains remain distinct at runtime"):
    val registry = sequentialRegistry("distinct-ordinals")
    val first = mustRight(registry.fresh("left", 3))
    val second = mustRight(first.registry.fresh("right", 3))
    val leftPoint = mustRight(first.space.point(1))
    val rightPoint = mustRight(second.space.point(1))

    assertEquals(leftPoint.value, rightPoint.value)
    assert(!leftPoint.equals(rightPoint))

  test("restoring an identical record through one registry reuses its live owner"):
    val record = mustRight(DomainRecord.parse("domain-stable", "stable", 5))
    val first = mustRight(DomainRegistry.empty.restore(record))
    val second = mustRight(first.registry.restore(record))

    assert(first.space.sameRuntimeOwnerAs(second.space))
    assertEquals(second.registry.size, 1)
    assert(
      mustRight(first.space.point(2))
        .equals(mustRight(second.space.point(2)))
    )

  test("restore rejects conflicting metadata for a registered DomainId"):
    val id = mustRight(DomainId.parse("domain-conflict"))
    val original = mustRight(DomainRecord.make(id, "original", 3))
    val conflictingName = mustRight(DomainRecord.make(id, "renamed", 3))
    val conflictingSize = mustRight(DomainRecord.make(id, "original", 4))
    val restored = mustRight(DomainRegistry.empty.restore(original))

    assertEquals(
      restored.registry.restore(conflictingName),
      Left(
        DomainRestoreError.ConflictingRecord(
          original,
          conflictingName
        )
      )
    )
    assertEquals(
      restored.registry.restore(conflictingSize),
      Left(
        DomainRestoreError.ConflictingRecord(
          original,
          conflictingSize
        )
      )
    )

  test("align recovers typed points, regions, and selections across registries"):
    val record = mustRight(DomainRecord.parse("domain-shared", "shared", 6))
    val left = mustRight(DomainRegistry.empty.restore(record))
    val right = mustRight(DomainRegistry.empty.restore(record))

    assert(!left.space.sameRuntimeOwnerAs(right.space))
    val alignment = mustRight(left.space.align(right.space))
    val leftPoint = mustRight(left.space.point(4))
    val rightPoint = mustRight(alignment.toRight(leftPoint))
    assert(right.space.contains(rightPoint))
    assertEquals(rightPoint, mustRight(right.space.point(4)))
    assert(!leftPoint.equals(rightPoint))

    val leftRegion =
      mustRight(Region.fromOrdinals(left.space, List(5, 1, 5)))
    val rightRegion = mustRight(alignment.regionToRight(leftRegion))
    assert(right.space.sameRuntimeOwnerAs(rightRegion.space))
    assertEquals(rightRegion.ordinalsInDomainOrder.toSeq, Seq(1, 5))

    val leftSelection =
      mustRight(Selection.fromOrdinals(left.space, List(5, 1)))
    val rightSelection =
      mustRight(alignment.selectionToRight(leftSelection))
    assertEquals(rightSelection.ordinals.toSeq, Seq(5, 1))

  test("align rejects equal ids with conflicting metadata"):
    val id = mustRight(DomainId.parse("domain-shared-id"))
    val firstRecord = mustRight(DomainRecord.make(id, "first", 2))
    val secondRecord = mustRight(DomainRecord.make(id, "second", 2))
    val first = mustRight(DomainRegistry.empty.restore(firstRecord))
    val second = mustRight(DomainRegistry.empty.restore(secondRecord))

    assertEquals(
      first.space.align(second.space),
      Left(DomainAlignmentError(firstRecord, secondRecord))
    )

  test("domain records reject empty identifiers, names, and negative sizes"):
    assertEquals(DomainId.parse(" \t"), Left(DomainError.EmptyId))
    val id = mustRight(DomainId.parse("valid"))
    assertEquals(DomainRecord.make(id, " ", 1), Left(DomainError.EmptyName))
    assertEquals(
      DomainRecord.make(id, "valid", -1),
      Left(DomainError.NegativeSize(-1))
    )

  test("fresh construction requires an explicit id source"):
    assertEquals(
      DomainRegistry.empty.fresh("missing-source", 1),
      Left(DomainFreshError.MissingIdSource)
    )

  test("sequential id sources are immutable and deterministic"):
    val source = mustRight(DomainIdSource.sequential("deterministic"))
    val firstStep = mustRight(source.next)
    val repeatedStep = mustRight(source.next)
    val secondStep = mustRight(firstStep.nextSource.next)

    assertEquals(firstStep.id.value, "deterministic-0")
    assertEquals(repeatedStep.id, firstStep.id)
    assertEquals(secondStep.id.value, "deterministic-1")

  test("fresh construction threads the next immutable id source"):
    val registry = sequentialRegistry("threaded")
    val first = mustRight(registry.fresh("first", 1))
    val second = mustRight(first.registry.fresh("second", 1))

    assertEquals(first.space.id.value, "threaded-0")
    assertEquals(second.space.id.value, "threaded-1")

  test("generated id collisions are typed and do not recurse"):
    val id = mustRight(DomainId.parse("collision-0"))
    val record = mustRight(DomainRecord.make(id, "existing", 1))
    val registry = sequentialRegistry("collision")
    val restored = mustRight(registry.restore(record))

    assertEquals(
      restored.registry.fresh("new", 1),
      Left(DomainFreshError.GeneratedIdCollision(id))
    )

  test("sequential source configuration rejects negative starts"):
    assertEquals(
      DomainIdSource.sequential("negative", -1L),
      Left(DomainIdSourceConfigError.NegativeStart(-1L))
    )

  private def sequentialRegistry(prefix: String): DomainRegistry =
    mustRight(DomainRegistry.withSequentialIds(prefix))

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) =>
        result
      case Left(error) =>
        fail(s"expected Right, found Left($error)")
