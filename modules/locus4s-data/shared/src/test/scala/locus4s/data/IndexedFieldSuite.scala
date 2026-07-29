package locus4s.data

import locus4s.DomainRecord
import locus4s.DomainRegistry
import locus4s.Region
import locus4s.Selection
import munit.FunSuite
import scala.collection.mutable.ArrayBuffer

final class IndexedFieldSuite extends FunSuite:
  private val resolution =
    restored("field-domain", "field", 6)
  private val space =
    resolution.space
  private val field =
    mustRight(
      IndexedField.fromValues(
        space,
        Vector(0, 10, 20, 30, 40, 50)
      )
    )

  test("construction validates size and owns caller-provided storage"):
    val input = ArrayBuffer(1, 2, 3, 4, 5, 6)
    val owned = mustRight(IndexedField.fromValues(space, input))
    input(0) = 99

    assertEquals(
      mustRight(owned.at(mustRight(space.point(0)))),
      1
    )
    assertEquals(
      IndexedField.fromValues(space, Vector(1, 2)),
      Left(IndexedFieldError.WrongValueCount(6, 2))
    )

  test("tabulate is strict and evaluates each domain point exactly once"):
    var evaluations = 0
    val tabulated =
      IndexedField.tabulate(space): point =>
        evaluations += 1
        point.value * 2

    assertEquals(evaluations, space.size)
    assertEquals(
      tabulated.valuesInDomainOrder.toVector,
      Vector(0, 2, 4, 6, 8, 10)
    )
    assertEquals(evaluations, space.size)

  test("restriction identity, nesting, and map naturality hold"):
    val whole = Region.whole(space)
    val first =
      mustRight(Region.fromOrdinals(space, Vector(0, 1, 3, 5)))
    val second =
      mustRight(Region.fromOrdinals(space, Vector(1, 2, 3)))

    assertEquals(
      mustRight(field.restrict(whole)).valuesInDomainOrder.toVector,
      field.valuesInDomainOrder.toVector
    )
    assertEquals(
      mustRight(mustRight(field.restrict(first)).restrict(second)).support,
      mustRight(first.intersect(second))
    )
    assertEquals(
      mustRight(field.map(_ + 1).restrict(first))
        .valuesInDomainOrder
        .toVector,
      mustRight(field.restrict(first))
        .map(_ + 1)
        .valuesInDomainOrder
        .toVector
    )

  test("section lookup and selection preserve support and explicit order"):
    val support =
      mustRight(Region.fromOrdinals(space, Vector(1, 3, 5)))
    val section = mustRight(field.restrict(support))
    val selection =
      mustRight(Selection.fromOrdinals(space, Vector(5, 1, 3)))

    assertEquals(
      section.at(mustRight(space.point(1))),
      Right(10)
    )
    assertEquals(
      section.at(mustRight(space.point(2))),
      Left(SectionLookupError.OutsideSupport(2))
    )
    assertEquals(
      section.valuesIn(selection),
      Right(Vector(50, 10, 30))
    )

    val outside =
      mustRight(Selection.fromOrdinals(space, Vector(1, 2)))
    assertEquals(
      section.valuesIn(outside),
      Left(SectionSelectionError.OutsideSupport(2))
    )

  test("runtime-domain mismatch is explicit even when records agree"):
    val record =
      mustRight(DomainRecord.parse("shared-field", "shared", 3))
    val left =
      mustRight(DomainRegistry.empty.restore(record))
    val right =
      mustRight(DomainRegistry.empty.restore(record))
    val leftField =
      mustRight(IndexedField.fromValues(left.space, Vector(2, 4, 6)))
    val rightWhole = Region.whole(right.space)

    leftField.restrict(rightWhole) match
      case Left(error) =>
        assert(error.persistentIdentityMatches)
        assertEquals(error.expected, record)
        assertEquals(error.actual, record)
      case Right(_) =>
        fail("distinct live owners must require explicit alignment")

    val alignment = mustRight(left.space.align(right.space))
    val rebound = mustRight(leftField.rebind(alignment))
    val reboundSection = mustRight(rebound.restrict(rightWhole))
    assertEquals(
      reboundSection.valuesInDomainOrder.toVector,
      Vector(2, 4, 6)
    )
    assert(rebound.toVector eq leftField.toVector)

  test("zipWith checks live domain ownership before combining"):
    val other =
      restored("other-field-domain", "other", space.size)
    val wrong =
      IndexedField.tabulate(other.space)(point => point.value)

    assert(field.zipWith(wrong)(_ + _).isLeft)
    assertEquals(
      mustRight(field.zipWith(field)(_ + _)).toVector,
      Vector(0, 20, 40, 60, 80, 100)
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
