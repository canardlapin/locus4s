package locus4s.data

import locus4s.DomainRecord
import locus4s.DomainRegistry
import locus4s.DomainResolution
import locus4s.Index
import locus4s.Region
import locus4s.Selection
import locus4s.TotalMap
import munit.FunSuite
import scala.collection.mutable.ArrayBuffer

final class IndexedFieldSuite extends FunSuite:
  private val resolution =
    restored("field-domain", 6)
  private val space =
    resolution.space
  private val field =
    mustRight(
      VectorField.fromValues(
        space,
        Vector(0, 10, 20, 30, 40, 50)
      )
    )

  test("VectorField owns input storage and lookup is total"):
    val input = ArrayBuffer(1, 2, 3, 4, 5, 6)
    val owned = mustRight(VectorField.fromValues(space, input))
    input(0) = 99

    assertEquals(owned(mustRight(space.index(0))), 1)
    assertEquals(
      VectorField.fromValues(space, Vector(1, 2)),
      Left(FieldConstructionError.WrongValueCount(6, 2))
    )

  test("Field supports arbitrary storage without copying through Vector"):
    final class FormulaField extends Field[resolution.S, Int]:
      val space = resolution.space

      def apply(index: Index[resolution.S]): Int =
        index.ordinal * index.ordinal

    val formula = new FormulaField

    assertEquals(
      formula.valuesInDomainOrder.toVector,
      Vector(0, 1, 4, 9, 16, 25)
    )

  test("map and Section map are O(1) views"):
    var evaluations = 0
    val mapped =
      field.map: value =>
        evaluations += 1
        value + 1
    val support =
      mustRight(Region.fromOrdinals(space, Vector(1, 3, 5)))
    val section = field.restrict(support)
    val mappedSection =
      section.map: value =>
        evaluations += 1
        value * 2

    assertEquals(evaluations, 0)
    assertEquals(mapped(mustRight(space.index(2))), 21)
    assertEquals(evaluations, 1)
    assertEquals(
      mappedSection(mustRight(space.index(3))),
      Right(60)
    )
    assertEquals(evaluations, 2)

  test("field pullback obeys identity and composition"):
    val first =
      mustRight(
        TotalMap.fromTargetOrdinals(
          space,
          space,
          Vector(1, 2, 3, 4, 5, 0)
        )
      )
    val second =
      mustRight(
        TotalMap.fromTargetOrdinals(
          space,
          space,
          Vector(5, 4, 3, 2, 1, 0)
        )
      )

    assertEquals(
      field.pullback(TotalMap.identity(space)).toVector,
      field.toVector
    )
    assertEquals(
      field.pullback(second).pullback(first).toVector,
      field.pullback(first.andThen(second)).toVector
    )

  test("selection gather retains an owned compact position domain"):
    val selection =
      mustRight(Selection.fromOrdinals(space, Vector(5, 1, 3)))
    val gathered = field.gather(selection)

    assert(gathered.space.sameRuntimeOwnerAs(selection.positions))
    assertEquals(gathered.toVector, Vector(50, 10, 30))
    selection.positions.indices.foreach: position =>
      assertEquals(
        gathered(position),
        field(selection(position))
      )

  test("Section gather checks support and returns a position-domain Field"):
    val support =
      mustRight(Region.fromOrdinals(space, Vector(1, 3, 5)))
    val section = field.restrict(support)
    val selection =
      mustRight(Selection.fromOrdinals(space, Vector(5, 1, 3)))
    val gathered = mustRight(section.gather(selection))

    assertEquals(gathered.toVector, Vector(50, 10, 30))
    val outside =
      mustRight(Selection.fromOrdinals(space, Vector(1, 2)))
    assertEquals(
      section.gather(outside),
      Left(SectionSelectionError.OutsideSupport(2))
    )

  test("alignment transports VectorField by sharing immutable storage"):
    val record =
      mustRight(DomainRecord.parse("shared-field", "left", 3))
    val renamed =
      mustRight(DomainRecord.parse("shared-field", "right", 3))
    val left =
      mustRight(DomainRegistry.empty.restore(record))
    val right =
      mustRight(DomainRegistry.empty.restore(renamed))
    val leftField =
      mustRight(VectorField.fromValues(left.space, Vector(2, 4, 6)))
    val alignment = mustRight(left.space.align(right.space))
    val rebound = leftField.rebind(alignment)

    assert(rebound.toVector eq leftField.toVector)
    assertEquals(rebound.toVector, Vector(2, 4, 6))
    assertEquals(
      alignment.transport(leftField).toVector,
      Vector(2, 4, 6)
    )

  test("checked field operations reject a different live owner"):
    val record =
      mustRight(DomainRecord.parse("checked-field", "left", 3))
    val renamed =
      mustRight(DomainRecord.parse("checked-field", "right", 3))
    val left =
      mustRight(DomainRegistry.empty.restore(record))
    val right =
      mustRight(DomainRegistry.empty.restore(renamed))
    val first =
      mustRight(VectorField.fromValues(left.space, Vector(1, 2, 3)))
    val second =
      mustRight(VectorField.fromValues(right.space, Vector(4, 5, 6)))

    first.zipWithChecked(second)(_ + _) match
      case Left(error) =>
        assert(error.persistentIdentityMatches)
      case Right(_) =>
        fail("checked zip must require one live owner")

  private def restored(id: String, size: Int): DomainResolution =
    val record = mustRight(DomainRecord.parse(id, id, size))
    mustRight(DomainRegistry.empty.restore(record))

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
