package locus4s.data

import locus4s.DomainRecord
import locus4s.DomainRegistry
import locus4s.Relation
import locus4s.TotalMap
import munit.FunSuite

final class AggregationSuite extends FunSuite:
  private val sourceResolution =
    restored("aggregation-source", "source", 6)
  private val targetResolution =
    restored("aggregation-target", "target", 3)
  private val source =
    sourceResolution.space
  private val target =
    targetResolution.space
  private val field =
    mustRight(
      IndexedField.fromValues(source, Vector(1, 2, 3, 4, 5, 6))
    )

  test("total-map aggregation folds every source in domain order"):
    val grouping =
      mustRight(
        TotalMap.fromTargetOrdinals(
          source,
          target,
          Vector(0, 0, 1, 1, 2, 2)
        )
      )
    var contributions = 0
    val result =
      mustRight(
        Aggregation.foldMapBy(grouping, field)(0)(
          value =>
            contributions += 1
            value
        )(_ + _)
      )

    assertEquals(contributions, source.size)
    assertEquals(result.toVector, Vector(3, 7, 11))

  test("relation aggregation supports empty and multi-target rows"):
    val grouping =
      mustRight(
        Relation.fromOrdinalRows(
          source,
          target,
          Vector(
            Vector(0),
            Vector(0, 1),
            Vector.empty,
            Vector(1),
            Vector(2),
            Vector(2)
          )
        )
      )
    var contributions = 0
    val result =
      mustRight(
        Aggregation.foldMapBy(grouping, field)(0)(
          value =>
            contributions += 1
            value
        )(_ + _)
      )

    assertEquals(contributions, 5)
    assertEquals(result.toVector, Vector(3, 6, 11))

  test("total-map aggregation fuses through map composition"):
    val parcelResolution =
      restored("aggregation-parcels", "parcels", 3)
    val networkResolution =
      restored("aggregation-networks", "networks", 2)
    val parcels = parcelResolution.space
    val networks = networkResolution.space
    val sourceToParcel =
      mustRight(
        TotalMap.fromTargetOrdinals(
          source,
          parcels,
          Vector(0, 0, 1, 1, 2, 2)
        )
      )
    val parcelToNetwork =
      mustRight(
        TotalMap.fromTargetOrdinals(
          parcels,
          networks,
          Vector(0, 0, 1)
        )
      )
    val directGrouping =
      mustRight(sourceToParcel.andThen(parcelToNetwork))

    val direct =
      mustRight(
        Aggregation.foldMapBy(directGrouping, field)(Set.empty[Int])(
          Set(_)
        )(_ union _)
      )
    val parcelValues =
      mustRight(
        Aggregation.foldMapBy(sourceToParcel, field)(Set.empty[Int])(
          Set(_)
        )(_ union _)
      )
    val hierarchical =
      mustRight(
        Aggregation.foldMapBy(
          parcelToNetwork,
          parcelValues
        )(Set.empty[Int])(identity)(_ union _)
      )

    assertEquals(hierarchical.toVector, direct.toVector)

  test("aggregation rejects distinct runtime owners until explicit rebind"):
    val record =
      mustRight(DomainRecord.parse("shared-source", "shared", 3))
    val left =
      mustRight(DomainRegistry.empty.restore(record))
    val right =
      mustRight(DomainRegistry.empty.restore(record))
    val output =
      restored("shared-output", "output", 1)
    val grouping =
      mustRight(
        TotalMap.fromTargetOrdinals(
          left.space,
          output.space,
          Vector(0, 0, 0)
        )
      )
    val wrongOwnerField =
      mustRight(
        IndexedField.fromValues(right.space, Vector(1, 2, 3))
      )

    Aggregation
      .foldMapBy(grouping, wrongOwnerField)(0)(identity)(_ + _) match
      case Left(error) =>
        assert(error.persistentIdentityMatches)
      case Right(_) =>
        fail("aggregation must reject distinct live owners")

    val alignment = mustRight(right.space.align(left.space))
    val rebound = mustRight(wrongOwnerField.rebind(alignment))
    assertEquals(
      mustRight(
        Aggregation.foldMapBy(grouping, rebound)(0)(identity)(_ + _)
      ).toVector,
      Vector(6)
    )

  private def restored(
      id: String,
      name: String,
      size: Int
  ) =
    val record = mustRight(DomainRecord.parse(id, name, size))
    mustRight(DomainRegistry.empty.restore(record))

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
