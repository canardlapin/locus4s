package locus4s.data

import locus4s.DomainRecord
import locus4s.DomainRegistry
import locus4s.DomainResolution
import locus4s.Index
import locus4s.PartialMap
import locus4s.PartialSurjection
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

  test(
    "partial-map pushforward skips undefined sources, preserves order, and retains empty targets"
  ):
    val grouping =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          source,
          target,
          Vector(Some(1), None, Some(1), Some(0), None, Some(1))
        )
      )
    val visited = Vector.newBuilder[Int]
    val result =
      Aggregation.foldMapBy(grouping, field)(Option.empty[Vector[Int]])(value =>
        visited += value
        Some(Vector(value))
      ):
        case (None, contribution)      => contribution
        case (accumulated, None)       => accumulated
        case (Some(left), Some(right)) => Some(left ++ right)

    assertEquals(visited.result(), Vector(1, 3, 4, 6))
    assertEquals(
      result.toVector,
      Vector(Some(Vector(4)), Some(Vector(1, 3, 6)), None)
    )

  test("partial-map pushforward consumes a representation-neutral field once"):
    val grouping =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          source,
          target,
          Vector(Some(0), None, Some(1), None, Some(2), None)
        )
      )
    var accesses = Vector.empty[Int]
    val imageBackedView =
      new Field[sourceResolution.S, Int]:
        val space = source

        def apply(index: Index[sourceResolution.S]): Int =
          accesses :+= index.ordinal
          (index.ordinal + 1) * 10

    val result =
      Aggregation.foldMapBy(grouping, imageBackedView)(0)(identity)(_ + _)

    assertEquals(accesses, Vector(0, 2, 4))
    assertEquals(result.toVector, Vector(10, 30, 50))

  test("partial-map checked pushforward rejects a distinct runtime owner"):
    val record =
      mustRight(DomainRecord.parse("checked-partial-source", "left", 3))
    val renamed =
      mustRight(DomainRecord.parse("checked-partial-source", "right", 3))
    val left =
      mustRight(DomainRegistry.empty.restore(record))
    val right =
      mustRight(DomainRegistry.empty.restore(renamed))
    val output = restored("checked-partial-output", 2).space
    val grouping =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          left.space,
          output,
          Vector(Some(0), None, Some(1))
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
        fail("checked partial pushforward must require one live owner")

  test("partial aggregation agrees with equivalent total aggregation"):
    val total =
      mustRight(
        TotalMap.fromTargetOrdinals(
          source,
          target,
          Vector(0, 0, 1, 1, 2, 2)
        )
      )
    val partial =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          source,
          target,
          Vector(Some(0), Some(0), Some(1), Some(1), Some(2), Some(2))
        )
      )
    val fromTotal =
      Aggregation.foldMapBy(total, field)(Vector.empty[Int])(Vector(_))(_ ++ _)
    val fromPartial =
      Aggregation.foldMapBy(partial, field)(Vector.empty[Int])(Vector(_))(
        _ ++ _
      )

    assertEquals(fromPartial.toVector, fromTotal.toVector)

  test("partial-surjection overload delegates and every result is non-empty"):
    val partial =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          source,
          target,
          Vector(Some(2), None, Some(0), Some(2), None, Some(1))
        )
      )
    val surjection = mustRight(PartialSurjection.fromPartialMap(partial))
    val combine =
      (left: Option[Vector[Int]], right: Option[Vector[Int]]) =>
        (left, right) match
          case (None, contribution) => contribution
          case (accumulated, None)  => accumulated
          case (Some(a), Some(b))   => Some(a ++ b)
    val direct =
      Aggregation.foldMapBy(partial, field)(Option.empty[Vector[Int]])(value =>
        Some(Vector(value))
      )(combine)
    val certified =
      Aggregation.foldMapBy(surjection, field)(Option.empty[Vector[Int]])(value =>
        Some(Vector(value))
      )(combine)

    assertEquals(certified.toVector, direct.toVector)
    assert(certified.toVector.forall(_.nonEmpty))

  test("partial-map aggregation covers empty and singleton domains"):
    val empty = restored("partial-empty", 0).space
    val emptyGrouping = PartialMap.empty(empty, empty)
    val emptyField = VectorField.tabulate(empty)(_.ordinal)
    assertEquals(
      Aggregation.foldMapBy(emptyGrouping, emptyField)(0)(identity)(_ + _).toVector,
      Vector.empty
    )

    val singletonSource = restored("partial-single-source", 1).space
    val singletonTarget = restored("partial-single-target", 1).space
    val singletonGrouping =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          singletonSource,
          singletonTarget,
          Vector(Some(0))
        )
      )
    val singletonField = VectorField.tabulate(singletonSource)(_ => 7)
    assertEquals(
      Aggregation
        .foldMapBy(singletonGrouping, singletonField)(0)(identity)(_ + _)
        .toVector,
      Vector(7)
    )

  private def restored(id: String, size: Int): DomainResolution =
    val record = mustRight(DomainRecord.parse(id, id, size))
    mustRight(DomainRegistry.empty.restore(record))

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
