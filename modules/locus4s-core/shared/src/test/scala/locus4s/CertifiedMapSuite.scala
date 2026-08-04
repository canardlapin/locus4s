package locus4s

import munit.FunSuite

final class CertifiedMapSuite extends FunSuite:
  private val source = restored("cert-source", 4).space
  private val target = restored("cert-target", 4).space

  test("Injection validates uniqueness and composes"):
    val first =
      mustRight(
        Injection.fromTargetOrdinals(
          source,
          target,
          Vector(2, 0, 3, 1)
        )
      )
    val second =
      mustRight(
        Injection.fromTargetOrdinals(
          target,
          target,
          Vector(1, 2, 3, 0)
        )
      )

    assertEquals(
      first.andThen(second).toTotalMap.targetOrdinals.toSeq,
      Seq(3, 1, 0, 2)
    )
    assertEquals(
      Injection.fromTargetOrdinals(
        source,
        target,
        Vector(0, 1, 1, 3)
      ),
      Left(CertifiedMapError.NotInjective(1, 2, 1))
    )

  test("Surjection validates complete target coverage"):
    val parcels = restored("parcels", 2).space
    val surjection =
      mustRight(
        Surjection.fromTargetOrdinals(
          source,
          parcels,
          Vector(0, 0, 1, 1)
        )
      )

    assert(surjection.toTotalMap.image(Region.whole(source)).isWhole)
    assertEquals(
      Surjection.fromTargetOrdinals(
        source,
        parcels,
        Vector(0, 0, 0, 0)
      ),
      Left(CertifiedMapError.NotSurjective(1))
    )

  test("Bijection has a lawful exact inverse"):
    val bijection =
      mustRight(
        Bijection.fromTotalMap(
          mustRight(
            TotalMap.fromTargetOrdinals(
              source,
              target,
              Vector(2, 0, 3, 1)
            )
          )
        )
      )

    assertEquals(
      bijection.andThen(bijection.inverse).toTotalMap,
      TotalMap.identity(source)
    )
    assertEquals(
      bijection.inverse.andThen(bijection).toTotalMap,
      TotalMap.identity(target)
    )

  test("PartialMap preserves undefined values through composition"):
    val middle = restored("partial-middle", 3).space
    val first =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          source,
          middle,
          Vector(Some(0), None, Some(2), Some(1))
        )
      )
    val second =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          middle,
          target,
          Vector(Some(3), None, Some(1))
        )
      )
    val composed = first.andThen(second)

    assertEquals(
      composed.optionalTargetOrdinals,
      Vector(Some(3), None, Some(1), None)
    )
    assertEquals(
      first.definedRegion.ordinalsInDomainOrder.toSeq,
      Seq(0, 2, 3)
    )

  test("PartialMap materializes preimages, its graph, and all fibers"):
    val parcels = restored("partial-fiber-parcels", 3).space
    val mapping =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          source,
          parcels,
          Vector(Some(2), None, Some(0), Some(2))
        )
      )

    assertEquals(
      mapping
        .preimage(mustRight(parcels.index(2)))
        .ordinalsInDomainOrder
        .toSeq,
      Seq(0, 3)
    )
    assertEquals(
      mustRight(mapping.toRelation).ordinalRows.map(_.toSeq).toSeq,
      Seq(Seq(2), Seq.empty, Seq(0), Seq(2))
    )
    assertEquals(
      mustRight(mapping.fibers).ordinalRows.map(_.toSeq).toSeq,
      Seq(Seq(2), Seq.empty, Seq(0, 3))
    )

  test("PartialSurjection validates coverage without a background target"):
    val parcels = restored("partial-parcels", 2).space
    val mapping =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          source,
          parcels,
          Vector(None, Some(0), Some(1), None)
        )
      )

    assert(PartialSurjection.fromPartialMap(mapping).isRight)

  test("PartialSurjection constructors validate bounds, owners, and coverage"):
    val parcels = restored("partial-constructor-parcels", 2).space
    val foreign = restored("partial-constructor-foreign", 2).space

    assertEquals(
      PartialSurjection.fromOptionalTargetOrdinals(
        source,
        parcels,
        Vector(Some(0), None, Some(2), Some(1))
      ),
      Left(PartialMapError.TargetOutOfBounds(2, 2, 2))
    )
    assertEquals(
      PartialSurjection.fromOptionalTargetOrdinals(
        source,
        parcels,
        Vector(Some(0), None, Some(0), None)
      ),
      Left(CertifiedMapError.NotSurjective(1))
    )

    val foreignTargets =
      Vector(
        Some(mustRight(foreign.index(0))),
        None,
        Some(mustRight(foreign.index(1))),
        None
      )
    PartialSurjection.fromOptionalTargetsChecked(
      source,
      parcels,
      foreign,
      foreignTargets
    ) match
      case Left(_: SpaceMismatch) => ()
      case result                 => fail(s"expected owner mismatch, found $result")

  test("PartialSurjection aligns optional targets from a restored owner"):
    val canonicalTargets = restored("aligned-partial-targets", 2).space
    val restoredTargets = restored("aligned-partial-targets", 2).space
    val alignment = mustRight(canonicalTargets.align(restoredTargets))
    val targets =
      Vector(
        Some(mustRight(restoredTargets.index(1))),
        None,
        Some(mustRight(restoredTargets.index(0))),
        Some(mustRight(restoredTargets.index(1)))
      )

    assert(!canonicalTargets.sameRuntimeOwnerAs(restoredTargets))

    val partition =
      mustRight(
        PartialSurjection.fromOptionalTargetsAligned(
          source,
          canonicalTargets,
          targets,
          alignment
        )
      )

    assertEquals(
      partition.toPartialMap.optionalTargetOrdinals,
      Vector(Some(1), None, Some(0), Some(1))
    )
    assertEquals(
      partition.support.ordinalsInDomainOrder.toSeq,
      Seq(0, 2, 3)
    )

  test("PartialSurjection exposes non-empty fibers and composes with coverage"):
    val parcels = restored("partial-compose-parcels", 2).space
    val groups = restored("partial-compose-groups", 1).space
    val partition =
      mustRight(
        PartialSurjection.fromOptionalTargetOrdinals(
          source,
          parcels,
          Vector(None, Some(0), Some(1), Some(0))
        )
      )
    val total =
      mustRight(Surjection.fromTargetOrdinals(parcels, groups, Vector(0, 0)))
    val partial =
      mustRight(
        PartialSurjection.fromOptionalTargetOrdinals(
          parcels,
          groups,
          Vector(None, Some(0))
        )
      )

    assertEquals(partition.support.ordinalsInDomainOrder.toSeq, Seq(1, 2, 3))
    parcels.foreachIndex: target =>
      assert(!partition.fiber(target).isEmpty)
    assertEquals(
      partition.andThen(total).support,
      partition.support
    )
    assertEquals(
      partition.andThen(partial).support.ordinalsInDomainOrder.toSeq,
      Seq(2)
    )
    assertEquals(
      mustRight(partition.fibers).converse,
      mustRight(partition.toRelation)
    )

  test("partition equivalence checks owners and ignores target relabeling"):
    val leftTargets = restored("partition-labels-left", 2).space
    val rightTargets = restored("partition-labels-right", 2).space
    val left =
      mustRight(
        PartialSurjection.fromOptionalTargetOrdinals(
          source,
          leftTargets,
          Vector(None, Some(0), Some(1), Some(0))
        )
      )
    val relabeled =
      mustRight(
        PartialSurjection.fromOptionalTargetOrdinals(
          source,
          rightTargets,
          Vector(None, Some(1), Some(0), Some(1))
        )
      )

    assertEquals(
      left.equivalentUpToTargetRelabelingChecked(relabeled),
      Right(true)
    )

    val restoredAgain = restored("cert-source", 4).space
    val onDistinctOwner =
      mustRight(
        PartialSurjection.fromOptionalTargetOrdinals(
          restoredAgain,
          rightTargets,
          Vector(None, Some(1), Some(0), Some(1))
        )
      )
    assert(left.equivalentUpToTargetRelabelingChecked(onDistinctOwner).isLeft)
    assert(
      left.equivalentUpToTargetRelabelingAligned(
        onDistinctOwner,
        mustRight(source.align(restoredAgain))
      )
    )

  test("empty-domain certified edge cases are explicit"):
    val emptySource = restored("empty-source", 0).space
    val emptyTarget = restored("empty-target", 0).space
    val nonEmpty = restored("nonempty-target", 1).space
    val emptyMap =
      mustRight(
        TotalMap.fromTargetOrdinals(
          emptySource,
          emptyTarget,
          Vector.empty
        )
      )
    val impossibleCoverage =
      mustRight(
        TotalMap.fromTargetOrdinals(
          emptySource,
          nonEmpty,
          Vector.empty
        )
      )

    assert(Injection.fromTotalMap(emptyMap).isRight)
    assert(Surjection.fromTotalMap(emptyMap).isRight)
    assert(Bijection.fromTotalMap(emptyMap).isRight)
    assertEquals(
      Surjection.fromTotalMap(impossibleCoverage),
      Left(CertifiedMapError.InsufficientSources(0, 1))
    )

  private def restored(id: String, size: Int): DomainResolution =
    val record = mustRight(DomainRecord.parse(id, id, size))
    mustRight(DomainRegistry.empty.restore(record))

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
