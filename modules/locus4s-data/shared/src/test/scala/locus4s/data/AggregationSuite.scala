package locus4s.data

import locus4s.DomainRecord
import locus4s.DomainRegistry
import locus4s.DomainResolution
import locus4s.Index
import locus4s.Relation
import locus4s.TotalMap
import munit.FunSuite

final class AggregationSuite extends FunSuite:
  private val sourceResolution =
    restored("aggregation-source", 6)
  private val targetResolution =
    restored("aggregation-target", 3)
  private val source =
    sourceResolution.space
  private val target =
    targetResolution.space
  private val field =
    mustRight(
      VectorField.fromValues(source, Vector(1, 2, 3, 4, 5, 6))
    )

  test("total-map pushforward folds every source in domain order"):
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
      Aggregation.pushForward(grouping, field)(0)(value =>
        contributions += 1
        value
      )(_ + _)

    assertEquals(contributions, source.size)
    assertEquals(result.toVector, Vector(3, 7, 11))

  test("relation pushforward skips empty rows and preserves source order"):
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
      Aggregation.pushForward(grouping, field)(Vector.empty[Int])(value =>
        contributions += 1
        Vector(value)
      )(_ ++ _)

    assertEquals(contributions, 5)
    assertEquals(
      result.toVector,
      Vector(Vector(1, 2), Vector(2, 4), Vector(5, 6))
    )

  test("pushforward fuses through total-map composition"):
    val parcels = restored("aggregation-parcels", 3).space
    val networks = restored("aggregation-networks", 2).space
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

    val direct =
      Aggregation.foldMapBy(
        sourceToParcel.andThen(parcelToNetwork),
        field
      )(Set.empty[Int])(Set(_))(_ union _)
    val intermediate =
      Aggregation.foldMapBy(sourceToParcel, field)(Set.empty[Int])(
        Set(_)
      )(_ union _)
    val staged =
      Aggregation.foldMapBy(parcelToNetwork, intermediate)(
        Set.empty[Int]
      )(identity)(_ union _)

    assertEquals(staged.toVector, direct.toVector)

  test("algorithms accept arbitrary Field and destination builder"):
    final class FormulaField extends Field[sourceResolution.S, Int]:
      val space = source

      def apply(index: Index[sourceResolution.S]): Int =
        index.ordinal + 1

    val grouping =
      mustRight(
        TotalMap.fromTargetOrdinals(
          source,
          target,
          Vector(0, 0, 1, 1, 2, 2)
        )
      )
    var builderCalls = 0
    val builder =
      new FieldBuilder:
        def tabulate[S, A](
            space: locus4s.FiniteDomain[S]
        )(valueAt: Index[S] => A): Field[S, A] =
          builderCalls += 1
          Field.view(space)(valueAt)
    val result =
      Aggregation.foldMapByWith(
        grouping,
        new FormulaField,
        builder
      )(0)(identity)(_ + _)

    assertEquals(builderCalls, 1)
    assertEquals(result.toVector, Vector(3, 7, 11))

  test("checked pushforward rejects a distinct runtime owner"):
    val record =
      mustRight(DomainRecord.parse("checked-source", "left", 3))
    val renamed =
      mustRight(DomainRecord.parse("checked-source", "right", 3))
    val left =
      mustRight(DomainRegistry.empty.restore(record))
    val right =
      mustRight(DomainRegistry.empty.restore(renamed))
    val output = restored("checked-output", 1).space
    val grouping =
      mustRight(
        TotalMap.fromTargetOrdinals(
          left.space,
          output,
          Vector(0, 0, 0)
        )
      )
    val wrongOwner =
      mustRight(VectorField.fromValues(right.space, Vector(1, 2, 3)))

    Aggregation.foldMapByChecked(grouping, wrongOwner)(0)(identity)(
      _ + _
    ) match
      case Left(error) =>
        assert(error.persistentIdentityMatches)
      case Right(_) =>
        fail("checked pushforward must require one live owner")

  private def restored(id: String, size: Int): DomainResolution =
    val record = mustRight(DomainRecord.parse(id, id, size))
    mustRight(DomainRegistry.empty.restore(record))

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
