package locus4s.consumer

import munit.FunSuite
import scala.compiletime.testing.typeCheckErrors

final class PackageBoundarySuite extends FunSuite:
  test("package membership cannot forge finite spaces or ordinals"):
    val spaceErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S](record: DomainRecord): FiniteSpace[S] =
          new FiniteSpace[S](record) {}
      """
    )
    val ordinalErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S](space: FiniteSpace[S]): Point[S] =
          new Ordinal[S](space, space.size) {}
      """
    )

    assert(spaceErrors.nonEmpty)
    assert(ordinalErrors.nonEmpty)

  test("package membership cannot call ownership-taking raw factories"):
    val regionErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S](space: FiniteSpace[S]) =
          Region.fromSortedOwned(space, Array(space.size))
      """
    )
    val selectionErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S](space: FiniteSpace[S]) =
          Selection.fromValidated(space, Array(space.size))
      """
    )
    val totalMapErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S](space: FiniteSpace[S]) =
          TotalMap.fromValidated(space, space, Array.emptyIntArray)
      """
    )
    val relationErrors = typeCheckErrors(
      """
        import locus4s.*
        def forge[S](space: FiniteSpace[S]) =
          Relation.fromValidated(space, space, Array.empty[Array[Int]])
      """
    )

    assert(regionErrors.nonEmpty)
    assert(selectionErrors.nonEmpty)
    assert(totalMapErrors.nonEmpty)
    assert(relationErrors.nonEmpty)
