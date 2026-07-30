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
