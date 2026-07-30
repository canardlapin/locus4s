package locus4s

import scala.collection.mutable

enum CertifiedMapError:
  case NotInjective(
      firstSource: Int,
      secondSource: Int,
      target: Int
  )
  case NotSurjective(missingTarget: Int)
  case InsufficientSources(fromSize: Int, toSize: Int)
  case DifferentCardinality(fromSize: Int, toSize: Int)

  def message: String =
    this match
      case NotInjective(first, second, target) =>
        s"sources $first and $second both map to target $target"
      case NotSurjective(missing) =>
        s"target $missing has no preimage"
      case InsufficientSources(fromSize, toSize) =>
        s"$fromSize sources cannot cover a target domain of size $toSize"
      case DifferentCardinality(fromSize, toSize) =>
        s"bijection requires equal cardinalities, found $fromSize and $toSize"

/** Certified one-to-one total map. */
final class Injection[X, Y] private (
    val toTotalMap: TotalMap[X, Y]
):
  def from: FiniteDomain[X] =
    toTotalMap.from

  def to: FiniteDomain[Y] =
    toTotalMap.to

  def apply(index: Index[X]): Index[Y] =
    toTotalMap(index)

  def support: Region[Y] =
    toTotalMap.image(Region.whole(from))

  def andThen[Z](that: Injection[Y, Z]): Injection[X, Z] =
    new Injection(toTotalMap.andThen(that.toTotalMap))

  def rebindFrom[A](
      alignment: DomainAlignment[X, A]
  ): Injection[A, Y] =
    new Injection(toTotalMap.rebindFrom(alignment))

  def rebindTo[B](
      alignment: DomainAlignment[Y, B]
  ): Injection[X, B] =
    new Injection(toTotalMap.rebindTo(alignment))

  override def equals(other: Any): Boolean =
    other match
      case that: Injection[?, ?] =>
        toTotalMap == that.toTotalMap
      case _ =>
        false

  override def hashCode(): Int =
    toTotalMap.hashCode()

object Injection:
  def fromTotalMap[X, Y](
      mapping: TotalMap[X, Y]
  ): Either[CertifiedMapError, Injection[X, Y]] =
    validateInjective(mapping).map(_ => new Injection(mapping))

  def fromTargetOrdinals[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      targets: IterableOnce[Int]
  ): Either[TotalMapError | CertifiedMapError, Injection[X, Y]] =
    TotalMap
      .fromTargetOrdinals(from, to, targets)
      .flatMap(fromTotalMap)

  def identity[S](space: FiniteDomain[S]): Injection[S, S] =
    new Injection(TotalMap.identity(space))

  def fromBijection[X, Y](
      bijection: Bijection[X, Y]
  ): Injection[X, Y] =
    new Injection(bijection.toTotalMap)

  private def validateInjective[X, Y](
      mapping: TotalMap[X, Y]
  ): Either[CertifiedMapError, Unit] =
    val firstSourceByTarget = mutable.HashMap.empty[Int, Int]
    var error = Option.empty[CertifiedMapError]
    mapping.foreachMapping: (source, target) =>
      if error.isEmpty then
        firstSourceByTarget.get(target.ordinal) match
          case Some(first) =>
            error = Some(
              CertifiedMapError.NotInjective(
                first,
                source.ordinal,
                target.ordinal
              )
            )
          case None =>
            firstSourceByTarget.update(target.ordinal, source.ordinal)
    error.toLeft(())

/** Certified onto total map. */
final class Surjection[X, Y] private (
    val toTotalMap: TotalMap[X, Y]
):
  def from: FiniteDomain[X] =
    toTotalMap.from

  def to: FiniteDomain[Y] =
    toTotalMap.to

  def apply(index: Index[X]): Index[Y] =
    toTotalMap(index)

  def andThen[Z](that: Surjection[Y, Z]): Surjection[X, Z] =
    new Surjection(toTotalMap.andThen(that.toTotalMap))

  def rebindFrom[A](
      alignment: DomainAlignment[X, A]
  ): Surjection[A, Y] =
    new Surjection(toTotalMap.rebindFrom(alignment))

  def rebindTo[B](
      alignment: DomainAlignment[Y, B]
  ): Surjection[X, B] =
    new Surjection(toTotalMap.rebindTo(alignment))

  override def equals(other: Any): Boolean =
    other match
      case that: Surjection[?, ?] =>
        toTotalMap == that.toTotalMap
      case _ =>
        false

  override def hashCode(): Int =
    toTotalMap.hashCode()

object Surjection:
  def fromTotalMap[X, Y](
      mapping: TotalMap[X, Y]
  ): Either[CertifiedMapError, Surjection[X, Y]] =
    CertifiedMapValidation
      .validateSurjective(mapping)
      .map(_ => new Surjection(mapping))

  def fromTargetOrdinals[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      targets: IterableOnce[Int]
  ): Either[TotalMapError | CertifiedMapError, Surjection[X, Y]] =
    TotalMap
      .fromTargetOrdinals(from, to, targets)
      .flatMap(fromTotalMap)

  def fromBijection[X, Y](
      bijection: Bijection[X, Y]
  ): Surjection[X, Y] =
    new Surjection(bijection.toTotalMap)

/** Certified finite-domain isomorphism. */
final class Bijection[X, Y] private (
    val toTotalMap: TotalMap[X, Y]
):
  def from: FiniteDomain[X] =
    toTotalMap.from

  def to: FiniteDomain[Y] =
    toTotalMap.to

  def apply(index: Index[X]): Index[Y] =
    toTotalMap(index)

  def inverse: Bijection[Y, X] =
    val targets = Array.ofDim[Int](to.size)
    toTotalMap.foreachMapping: (source, target) =>
      targets(target.ordinal) = source.ordinal
    new Bijection(
      TotalMap.tabulate(to, from)(target =>
        from.indexAtOrdinal(targets(target.ordinal))
      )
    )

  def andThen[Z](that: Bijection[Y, Z]): Bijection[X, Z] =
    new Bijection(toTotalMap.andThen(that.toTotalMap))

  def toInjection: Injection[X, Y] =
    Injection.fromBijection(this)

  def toSurjection: Surjection[X, Y] =
    Surjection.fromBijection(this)

  def rebindFrom[A](
      alignment: DomainAlignment[X, A]
  ): Bijection[A, Y] =
    new Bijection(toTotalMap.rebindFrom(alignment))

  def rebindTo[B](
      alignment: DomainAlignment[Y, B]
  ): Bijection[X, B] =
    new Bijection(toTotalMap.rebindTo(alignment))

  override def equals(other: Any): Boolean =
    other match
      case that: Bijection[?, ?] =>
        toTotalMap == that.toTotalMap
      case _ =>
        false

  override def hashCode(): Int =
    toTotalMap.hashCode()

object Bijection:
  def fromTotalMap[X, Y](
      mapping: TotalMap[X, Y]
  ): Either[CertifiedMapError, Bijection[X, Y]] =
    if mapping.from.size != mapping.to.size then
      Left(
        CertifiedMapError.DifferentCardinality(
          mapping.from.size,
          mapping.to.size
        )
      )
    else
      Injection
        .fromTotalMap(mapping)
        .map(_ => new Bijection(mapping))

  def identity[S](space: FiniteDomain[S]): Bijection[S, S] =
    new Bijection(TotalMap.identity(space))

/** Partial map whose defined image covers the complete target domain. */
final class PartialSurjection[X, Y] private (
    val toPartialMap: PartialMap[X, Y]
):
  def from: FiniteDomain[X] =
    toPartialMap.from

  def to: FiniteDomain[Y] =
    toPartialMap.to

  def apply(index: Index[X]): Option[Index[Y]] =
    toPartialMap(index)

  def rebindFrom[A](
      alignment: DomainAlignment[X, A]
  ): PartialSurjection[A, Y] =
    new PartialSurjection(toPartialMap.rebindFrom(alignment))

  def rebindTo[B](
      alignment: DomainAlignment[Y, B]
  ): PartialSurjection[X, B] =
    new PartialSurjection(toPartialMap.rebindTo(alignment))

  override def equals(other: Any): Boolean =
    other match
      case that: PartialSurjection[?, ?] =>
        toPartialMap == that.toPartialMap
      case _ =>
        false

  override def hashCode(): Int =
    toPartialMap.hashCode()

object PartialSurjection:
  def fromPartialMap[X, Y](
      mapping: PartialMap[X, Y]
  ): Either[CertifiedMapError, PartialSurjection[X, Y]] =
    if mapping.from.size < mapping.to.size then
      Left(
        CertifiedMapError.InsufficientSources(
          mapping.from.size,
          mapping.to.size
        )
      )
    else
      val reached = Array.fill(mapping.to.size)(false)
      mapping.foreachDefined: (_, target) =>
        reached(target.ordinal) = true
      val missing = reached.indexWhere(value => !value)
      if missing >= 0 then Left(CertifiedMapError.NotSurjective(missing))
      else Right(new PartialSurjection(mapping))

private object CertifiedMapValidation:
  def validateSurjective[X, Y](
      mapping: TotalMap[X, Y]
  ): Either[CertifiedMapError, Unit] =
    if mapping.from.size < mapping.to.size then
      Left(
        CertifiedMapError.InsufficientSources(
          mapping.from.size,
          mapping.to.size
        )
      )
    else
      val reached = Array.fill(mapping.to.size)(false)
      mapping.foreachMapping: (_, target) =>
        reached(target.ordinal) = true
      val missing = reached.indexWhere(value => !value)
      if missing >= 0 then Left(CertifiedMapError.NotSurjective(missing))
      else Right(())
