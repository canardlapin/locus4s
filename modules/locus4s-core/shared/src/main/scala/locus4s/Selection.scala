package locus4s

import scala.collection.mutable

enum SelectionError:
  case OutOfBounds(position: Int, pointOrdinal: Int, size: Int)
  case DuplicateOrdinal(pointOrdinal: Int)
  case InvalidInjection(error: CertifiedMapError)

  def message: String =
    this match
      case OutOfBounds(position, pointOrdinal, size) =>
        s"selection ordinal at position $position is outside [0, $size): $pointOrdinal"
      case DuplicateOrdinal(pointOrdinal) =>
        s"selection contains duplicate ordinal $pointOrdinal"
      case InvalidInjection(error) =>
        error.message

/** An ordered finite selection represented by an injection into its source.
  *
  * The path-dependent position domain `I` owns the compact ordering. Gathering through
  * `embedding` therefore produces values on a real finite domain rather than an
  * identity-free collection.
  */
sealed trait Selection[S]:
  type I

  val space: FiniteDomain[S]
  val positions: FiniteDomain[I]
  val embedding: Injection[I, S]
  val support: Region[S]

  final def region: Region[S] =
    support

  final def size: Int =
    positions.size

  final def isEmpty: Boolean =
    positions.size == 0

  final def apply(position: Index[I]): Index[S] =
    embedding(position)

  final def get(position: Int): Option[Index[S]] =
    positions.indexOption(position).map(embedding.apply)

  final def foreachIndex(f: Index[S] => Unit): Unit =
    positions.foreachIndex(position => f(embedding(position)))

  final def indices: Iterator[Index[S]] =
    positions.indices.map(embedding.apply)

  /** Compatibility spelling for `indices`. */
  @deprecated("Use indices; scheduled for removal in 1.0.", "0.1.0")
  final def points: Iterator[Index[S]] =
    indices

  /** Dynamic-boundary copy in selection-position order. */
  final def ordinals: Array[Int] =
    embedding.toTotalMap.targetOrdinals

  /** O(1) source transport sharing positions and target storage. */
  def rebind[B](alignment: DomainAlignment[S, B]): Selection[B]

object Selection:
  type Aux[S, J] = Selection[S] { type I = J }

  private final class Impl[S, J](
      val space: FiniteDomain[S],
      val positions: FiniteDomain[J],
      val embedding: Injection[J, S],
      val support: Region[S]
  ) extends Selection[S]:
    type I = J

    def rebind[B](alignment: DomainAlignment[S, B]): Selection[B] =
      new Impl(
        alignment.right,
        positions,
        embedding.rebindTo(alignment),
        support.rebind(alignment)
      )

    override def equals(other: Any): Boolean =
      other match
        case that: Selection[?] =>
          space == that.space &&
          Selection.sameOrdinals(ordinals, that.ordinals)
        case _ =>
          false

    override def hashCode(): Int =
      var result = space.hashCode()
      val values = ordinals
      var index = 0
      while index < values.length do
        result = 31 * result + values(index)
        index += 1
      result

    override def toString: String =
      s"Selection(${space.name.value}, size=$size)"

  def fromEmbedding[I, S](
      embedding: Injection[I, S]
  ): Selection.Aux[S, I] =
    new Impl(
      embedding.to,
      embedding.from,
      embedding,
      embedding.support
    )

  def empty[S](
      space: FiniteDomain[S]
  ): Either[SelectionError, Selection[S]] =
    fromValidatedOrdinals(space, Array.emptyIntArray)

  def fromRegion[S](
      region: Region[S]
  ): Either[SelectionError, Selection[S]] =
    fromValidatedOrdinals(region.space, region.ordinalsInDomainOrder)

  def fromOrdinals[S](
      space: FiniteDomain[S],
      ordinals: IterableOnce[Int]
  ): Either[SelectionError, Selection[S]] =
    val input = ordinals.iterator.toArray
    val seen = mutable.HashSet.empty[Int]
    var position = 0
    var error = Option.empty[SelectionError]
    while position < input.length && error.isEmpty do
      val ordinal = input(position)
      if !space.containsOrdinal(ordinal) then
        error = Some(
          SelectionError.OutOfBounds(
            position,
            ordinal,
            space.size
          )
        )
      else if !seen.add(ordinal) then
        error = Some(SelectionError.DuplicateOrdinal(ordinal))
      position += 1

    error match
      case Some(value) => Left(value)
      case None        => fromValidatedOrdinals(space, input)

  def fromIndices[S](
      space: FiniteDomain[S],
      indices: IterableOnce[Index[S]]
  ): Either[SelectionError, Selection[S]] =
    fromOrdinals(space, indices.iterator.map(_.ordinal))

  /** Compatibility spelling for `fromIndices`. */
  @deprecated("Use fromIndices; scheduled for removal in 1.0.", "0.1.0")
  def fromPoints[S](
      space: FiniteDomain[S],
      points: IterableOnce[Index[S]]
  ): Either[SelectionError, Selection[S]] =
    fromIndices(space, points)

  private def fromValidatedOrdinals[S](
      space: FiniteDomain[S],
      ordered: Array[Int]
  ): Either[SelectionError, Selection[S]] =
    val positionName =
      DomainName
        .parse(s"${space.name.value} selection positions")
        .fold(_ => space.name, identity)
    val packed =
      FiniteDomain.ephemeralValidated(positionName, ordered.length)
    val positions = packed.value
    val mapping =
      TotalMap.tabulate(positions, space)(position =>
        space.indexAtOrdinal(ordered(position.ordinal))
      )
    Injection
      .fromTotalMap(mapping)
      .left
      .map(SelectionError.InvalidInjection.apply)
      .map(embedding => new Impl(space, positions, embedding, embedding.support))

  private def sameOrdinals(
      left: Array[Int],
      right: Array[Int]
  ): Boolean =
    if left.length != right.length then false
    else
      var index = 0
      var same = true
      while index < left.length && same do
        same = left(index) == right(index)
        index += 1
      same
