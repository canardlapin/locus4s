package locus4s.data

import munit.FunSuite
import scala.compiletime.testing.typeCheckErrors

final class PackageJoiningBoundarySuite extends FunSuite:
  test("typed field lookup is total for its owner"):
    val errors = typeCheckErrors(
      """
        import locus4s.*
        import locus4s.data.*
        def lookup[S, A](field: Field[S, A], index: Index[S]): A =
          field(index)
      """
    )
    assertEquals(errors, Nil)

  test("field lookup rejects an unrelated owner type"):
    val errors = typeCheckErrors(
      """
        import locus4s.*
        import locus4s.data.*
        def invalid[A, B](
          field: Field[A, Int],
          index: Index[B]
        ): Int =
          field(index)
      """
    )
    assert(errors.nonEmpty)

  test("consumers cannot construct concrete fields or call owned factories"):
    val constructorErrors = typeCheckErrors(
      """
        import locus4s.*
        import locus4s.data.*
        def forge[S, A](space: FiniteDomain[S], values: Vector[A]) =
          new VectorField(space, values)
      """
    )
    val ownedFactoryErrors = typeCheckErrors(
      """
        import locus4s.*
        import locus4s.data.*
        def forge[S, A](space: FiniteDomain[S], values: Vector[A]) =
          VectorField.fromOwned(space, values)
      """
    )

    assert(constructorErrors.nonEmpty)
    assert(ownedFactoryErrors.nonEmpty)

  test("data foundation exposes no imaging policy API"):
    val errors = typeCheckErrors(
      """
        import locus4s.data.*
        val parcellation = Parcellation
        val searchlight = Searchlight
        val image: Image[?, ?, ?, ?, ?] = ???
      """
    )
    assert(errors.nonEmpty)
