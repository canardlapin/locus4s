package locus4s.consumer

import munit.FunSuite
import scala.compiletime.testing.typeCheckErrors

final class PackageBoundarySuite extends FunSuite:
  test("consumers cannot forge domains, indices, or mismatch facts"):
    val domainErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S](name: DomainName): FiniteDomain[S] =
          new FiniteDomain[S](name, 1, None) {}
      """
    )
    val indexErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S]: Index[S] =
          Index.unsafeFromOrdinal[S](0)
      """
    )
    val mismatchErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge(
          expected: DomainDescriptor,
          actual: DomainDescriptor
        ): SpaceMismatch =
          new SpaceMismatch(expected, actual)
      """
    )

    assert(domainErrors.nonEmpty)
    assert(indexErrors.nonEmpty)
    assert(mismatchErrors.nonEmpty)

  test("typed total operations reject unrelated owner types at compile time"):
    val regionErrors = typeCheckErrors(
      """
        import locus4s.*
        def invalid[A, B](
          left: Region[A],
          right: Region[B]
        ): Region[A] =
          left.union(right)
      """
    )
    val mapErrors = typeCheckErrors(
      """
        import locus4s.*
        def invalid[A, B, C, D](
          first: TotalMap[A, B],
          second: TotalMap[C, D]
        ): TotalMap[A, D] =
          first.andThen(second)
      """
    )

    assert(regionErrors.nonEmpty)
    assert(mapErrors.nonEmpty)

  test("consumers cannot call ownership-taking primitive factories"):
    val regionErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S](space: FiniteDomain[S]) =
          Region.fromSortedOwned(space, Array(space.size))
      """
    )
    val totalMapErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S](space: FiniteDomain[S]) =
          TotalMap.fromOwned(space, space, Array.emptyIntArray)
      """
    )
    val relationErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S](space: FiniteDomain[S]) =
          Relation.fromOwnedCsr(
            space,
            space,
            Array(0),
            Array.emptyIntArray
          )
      """
    )

    assert(regionErrors.nonEmpty)
    assert(totalMapErrors.nonEmpty)
    assert(relationErrors.nonEmpty)

  test("immutable registry has no branching fresh-id API"):
    val errors = typeCheckErrors(
      """
        import locus4s.*
        DomainRegistry.empty.fresh("name", 1)
      """
    )
    assert(errors.nonEmpty)
