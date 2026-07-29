package locus4s.data

import munit.FunSuite
import scala.compiletime.testing.typeCheckErrors

final class PackageJoiningBoundarySuite extends FunSuite:
  test("consumers cannot construct fields or call ownership-taking internals"):
    val packageJoiningControl = typeCheckErrors(
      """
        import locus4s.*
        def checked[S, A](
          space: FiniteSpace[S],
          values: Vector[A]
        ) =
          IndexedField.fromValues(space, values)
      """
    )
    val constructorErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S, A](space: FiniteSpace[S], values: Vector[A]) =
          new IndexedField(space, values)
      """
    )
    val ownedFactoryErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S, A](space: FiniteSpace[S], values: Vector[A]) =
          IndexedField.fromOwned(space, values)
      """
    )
    val ordinalLookupErrors = typeCheckErrors(
      """
        def forge[S, A](field: IndexedField[S, A]) =
          field.valueAtOwnedOrdinal(0)
      """
    )
    val sectionConstructorErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S, A](
          field: IndexedField[S, A],
          support: Region[S]
        ) =
          new Section(field, support)
      """
    )

    assertEquals(packageJoiningControl, Nil)
    assert(constructorErrors.nonEmpty)
    assert(ownedFactoryErrors.nonEmpty)
    assert(ordinalLookupErrors.nonEmpty)
    assert(sectionConstructorErrors.nonEmpty)

  test("field lookup cannot silently bypass runtime-domain validation"):
    val errors = typeCheckErrors(
      """
        import locus4s.*
        def unsafe[S, A](field: IndexedField[S, A], point: Point[S]): A =
          field(point)
      """
    )
    assert(errors.nonEmpty)

  test("data foundation exposes no parcellation, searchlight, or image API"):
    val parcellationErrors = typeCheckErrors(
      """
        import locus4s.data.*
        val value = Parcellation
      """
    )
    val searchlightErrors = typeCheckErrors(
      """
        import locus4s.data.*
        val value = Searchlight
      """
    )
    val imagingErrors = typeCheckErrors(
      """
        import locus4s.data.*
        val value: Image[?, ?, ?, ?, ?] = ???
      """
    )

    assert(parcellationErrors.nonEmpty)
    assert(searchlightErrors.nonEmpty)
    assert(imagingErrors.nonEmpty)
