package locus4s

import munit.FunSuite

final class DomainSuite extends FunSuite:
  test("validated structures can recover indices without a checked wrapper"):
    val packed =
      mustRight(FiniteDomain.ephemeral("validated ordinals", 3))
    val domain = packed.value
    val index = domain.indexAtValidatedOrdinal(2)

    assertEquals(index.ordinal, 2)
    intercept[IndexOutOfBoundsException] {
      domain.indexAtValidatedOrdinal(3)
    }

  test("registry canonicalizes supplied structural identity"):
    val original =
      mustRight(DomainRecord.parse("stable", "original label", 5))
    val renamed =
      mustRight(DomainRecord.parse("stable", "renamed label", 5))
    val first = mustRight(DomainRegistry.empty.register(original))
    val second = mustRight(first.registry.restore(renamed))

    assert(first.space.sameRuntimeOwnerAs(second.space))
    assertEquals(second.registry.size, 1)
    assertEquals(second.space.record, original)
    assert(first.space.samePersistentIdentityAs(second.space))

  test("metadata changes align across independent registries"):
    val original =
      mustRight(DomainRecord.parse("shared", "first label", 4))
    val renamed =
      mustRight(DomainRecord.parse("shared", "second label", 4))
    val left = mustRight(DomainRegistry.empty.restore(original))
    val right = mustRight(DomainRegistry.empty.restore(renamed))
    val alignment = mustRight(left.space.align(right.space))
    val index = mustRight(left.space.index(3))

    assert(!left.space.sameRuntimeOwnerAs(right.space))
    assertEquals(alignment.toRight(index).ordinal, 3)
    assertEquals(alignment.reverse.toRight(alignment.toRight(index)), index)

  test("registry rejects incompatible size and fingerprint for one id"):
    val original =
      mustRight(
        DomainRecord.parse(
          "conflict",
          "domain",
          3,
          Some("grid-a")
        )
      )
    val wrongSize =
      mustRight(
        DomainRecord.parse(
          "conflict",
          "domain",
          4,
          Some("grid-a")
        )
      )
    val wrongFingerprint =
      mustRight(
        DomainRecord.parse(
          "conflict",
          "domain",
          3,
          Some("grid-b")
        )
      )
    val restored = mustRight(DomainRegistry.empty.restore(original))

    assertEquals(
      restored.registry.restore(wrongSize),
      Left(DomainRestoreError.ConflictingKey(original, wrongSize))
    )
    assertEquals(
      restored.registry.restore(wrongFingerprint),
      Left(
        DomainRestoreError.ConflictingKey(
          original,
          wrongFingerprint
        )
      )
    )

  test("ephemeral derived owners align only with themselves"):
    val first = mustRight(FiniteDomain.ephemeral("positions", 3))
    val second = mustRight(FiniteDomain.ephemeral("positions", 3))
    val index = mustRight(first.value.index(2))

    assert(first.value.align(second.value).isLeft)
    val identity = DomainAlignment.identity(first.value)
    assertEquals(identity.toRight(index), index)
    assert(!first.value.isPersistable)

  test("alignment identity inverse and composition form a groupoid"):
    val record =
      mustRight(DomainRecord.parse("groupoid", "groupoid", 6))
    val first = mustRight(DomainRegistry.empty.restore(record))
    val second = mustRight(DomainRegistry.empty.restore(record))
    val third = mustRight(DomainRegistry.empty.restore(record))
    val fourth = mustRight(DomainRegistry.empty.restore(record))
    val ab = mustRight(first.space.align(second.space))
    val bc = mustRight(second.space.align(third.space))
    val cd = mustRight(third.space.align(fourth.space))
    val index = mustRight(first.space.index(4))

    assertEquals(
      ab.andThen(bc).andThen(cd).toRight(index),
      ab.andThen(bc.andThen(cd)).toRight(index)
    )
    assertEquals(ab.reverse.toRight(ab.toRight(index)), index)
    assertEquals(
      DomainAlignment.identity(first.space).toRight(index),
      index
    )

  test("alignment transports core values and shares immutable storage"):
    val record =
      mustRight(DomainRecord.parse("transport", "transport", 6))
    val left = mustRight(DomainRegistry.empty.restore(record))
    val right = mustRight(DomainRegistry.empty.restore(record))
    val alignment = mustRight(left.space.align(right.space))
    val region =
      mustRight(Region.fromOrdinals(left.space, Vector(1, 3, 5)))
    val selection =
      mustRight(Selection.fromOrdinals(left.space, Vector(5, 1, 3)))
    val mapping =
      mustRight(
        TotalMap.fromTargetOrdinals(
          left.space,
          left.space,
          Vector(1, 2, 3, 4, 5, 0)
        )
      )

    assertEquals(
      alignment.reverse.transport(alignment.transport(region)),
      region
    )
    assertEquals(
      alignment.reverse.transport(alignment.transport(selection)),
      selection
    )
    assertEquals(
      alignment
        .transportFrom(mapping)
        .targetOrdinals
        .toSeq,
      mapping.targetOrdinals.toSeq
    )

  test("domain validation includes fingerprint and huge declared sizes"):
    assertEquals(DomainId.parse(" \t"), Left(DomainError.EmptyId))
    assertEquals(
      DomainFingerprint.parse(" "),
      Left(DomainError.EmptyFingerprint)
    )
    val record =
      mustRight(
        DomainRecord.parse(
          "huge",
          "huge",
          Int.MaxValue,
          Some("opaque")
        )
      )
    val restored = mustRight(DomainRegistry.empty.restore(record))
    val last = mustRight(restored.space.index(Int.MaxValue - 1))

    assertEquals(restored.space.size, Int.MaxValue)
    assertEquals(last.ordinal, Int.MaxValue - 1)
    assertEquals(
      restored.space.index(Int.MaxValue),
      Left(IndexError.OutOfBounds(Int.MaxValue, Int.MaxValue))
    )

  test("mismatch persistent identity fact is derived"):
    val record =
      mustRight(DomainRecord.parse("same-key", "left", 2))
    val renamed =
      mustRight(DomainRecord.parse("same-key", "right", 2))
    val left = mustRight(DomainRegistry.empty.restore(record))
    val right = mustRight(DomainRegistry.empty.restore(renamed))
    val leftRegion = Region.whole(left.space)
    val rightRegion = Region.whole(right.space)

    leftRegion.unionChecked(rightRegion) match
      case Left(error) =>
        assert(error.persistentIdentityMatches)
      case Right(_) =>
        fail("checked operation must require one live owner")

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
