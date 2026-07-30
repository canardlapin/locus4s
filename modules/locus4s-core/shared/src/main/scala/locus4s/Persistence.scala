package locus4s

enum RegionEncoding:
  case Empty
  case Whole
  case SortedOrdinals(ordinals: Vector[Int])

final case class RegionRecord(
    domain: DomainKey,
    encoding: RegionEncoding
)

final case class SelectionRecord(
    source: DomainKey,
    positions: DomainRecord,
    ordinals: Vector[Int]
)

final case class TotalMapRecord(
    from: DomainKey,
    to: DomainKey,
    targets: Vector[Int]
)

final case class PartialMapRecord(
    from: DomainKey,
    to: DomainKey,
    targets: Vector[Option[Int]]
)

enum RelationEncoding:
  case Empty
  case Csr(rowOffsets: Vector[Int], targets: Vector[Int])

final case class RelationRecord(
    from: DomainKey,
    to: DomainKey,
    encoding: RelationEncoding
)

enum PersistenceError:
  case EphemeralDomain(role: String, domain: DomainDescriptor)
  case DomainKeyMismatch(
      role: String,
      expected: DomainKey,
      actual: DomainKey
  )
  case PositionSizeMismatch(expected: Int, actual: Int)
  case InvalidRegion(error: RegionError)
  case InvalidSelection(error: SelectionError)
  case InvalidTotalMap(error: TotalMapError)
  case InvalidPartialMap(error: PartialMapError)
  case InvalidRelation(error: RelationError)
  case InvalidCertifiedMap(error: CertifiedMapError)
  case RegistryFailure(error: DomainRestoreError)

  def message: String =
    this match
      case EphemeralDomain(role, domain) =>
        s"$role uses the ephemeral domain $domain"
      case DomainKeyMismatch(role, expected, actual) =>
        s"$role domain key mismatch: expected $expected, found $actual"
      case PositionSizeMismatch(expected, actual) =>
        s"selection positions require size $expected, found $actual"
      case InvalidRegion(error) =>
        error.message
      case InvalidSelection(error) =>
        error.message
      case InvalidTotalMap(error) =>
        error.message
      case InvalidPartialMap(error) =>
        error.message
      case InvalidRelation(error) =>
        error.message
      case InvalidCertifiedMap(error) =>
        error.message
      case RegistryFailure(error) =>
        error.message

final case class SelectionRestoration[S](
    registry: DomainRegistry,
    selection: Selection[S]
)

/** Neutral persistence records and checked reconstruction.
  *
  * These records have no codec dependency. JSON, CBOR, database, and imaging extension
  * codecs may encode them downstream. Every reconstruction validates endpoint keys and
  * structural invariants before creating a live value.
  */
object Persistence:
  def record[S](
      region: Region[S]
  ): Either[PersistenceError, RegionRecord] =
    persistentKey(region.space, "region").map: key =>
      val encoding =
        if region.isEmpty then RegionEncoding.Empty
        else if region.isWhole then RegionEncoding.Whole
        else
          RegionEncoding.SortedOrdinals(
            region.ordinalsInDomainOrder.toVector
          )
      RegionRecord(key, encoding)

  def restore[S](
      space: FiniteDomain[S],
      record: RegionRecord
  ): Either[PersistenceError, Region[S]] =
    for
      actual <- persistentKey(space, "region")
      _ <- requireKey("region", record.domain, actual)
      region <-
        record.encoding match
          case RegionEncoding.Empty =>
            Right(Region.empty(space))
          case RegionEncoding.Whole =>
            Right(Region.whole(space))
          case RegionEncoding.SortedOrdinals(ordinals) =>
            Region
              .fromSortedDistinct(space, ordinals)
              .left
              .map(PersistenceError.InvalidRegion.apply)
    yield region

  /** Persist a selection whose position domain is already persistable. */
  def record[S](
      selection: Selection[S]
  ): Either[PersistenceError, SelectionRecord] =
    selection.positions.persistentRecord match
      case Some(positionRecord) =>
        record(selection, positionRecord)
      case None =>
        Left(
          PersistenceError.EphemeralDomain(
            "selection positions",
            selection.positions.descriptor
          )
        )

  /** Assign an explicit persistent identity to ephemeral selection positions. */
  def record[S](
      selection: Selection[S],
      positionRecord: DomainRecord
  ): Either[PersistenceError, SelectionRecord] =
    for
      sourceKey <- persistentKey(selection.space, "selection source")
      _ <-
        if positionRecord.size == selection.size then Right(())
        else
          Left(
            PersistenceError.PositionSizeMismatch(
              selection.size,
              positionRecord.size
            )
          )
      _ <-
        selection.positions.persistentKey match
          case Some(existing) =>
            requireKey("selection positions", existing, positionRecord.key)
          case None =>
            Right(())
    yield SelectionRecord(
      sourceKey,
      positionRecord,
      selection.ordinals.toVector
    )

  def restore[S](
      source: FiniteDomain[S],
      record: SelectionRecord,
      registry: DomainRegistry
  ): Either[PersistenceError, SelectionRestoration[S]] =
    for
      sourceKey <- persistentKey(source, "selection source")
      _ <- requireKey("selection source", record.source, sourceKey)
      _ <-
        if record.positions.size == record.ordinals.size then Right(())
        else
          Left(
            PersistenceError.PositionSizeMismatch(
              record.ordinals.size,
              record.positions.size
            )
          )
      positionResolution <-
        registry
          .restore(record.positions)
          .left
          .map(PersistenceError.RegistryFailure.apply)
      mapping <-
        TotalMap
          .fromTargetOrdinals(
            positionResolution.space,
            source,
            record.ordinals
          )
          .left
          .map(PersistenceError.InvalidTotalMap.apply)
      embedding <-
        Injection
          .fromTotalMap(mapping)
          .left
          .map(PersistenceError.InvalidCertifiedMap.apply)
    yield SelectionRestoration(
      positionResolution.registry,
      Selection.fromEmbedding(embedding)
    )

  def record[X, Y](
      mapping: TotalMap[X, Y]
  ): Either[PersistenceError, TotalMapRecord] =
    for
      fromKey <- persistentKey(mapping.from, "total-map source")
      toKey <- persistentKey(mapping.to, "total-map target")
    yield TotalMapRecord(
      fromKey,
      toKey,
      mapping.targetOrdinals.toVector
    )

  def restore[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      record: TotalMapRecord
  ): Either[PersistenceError, TotalMap[X, Y]] =
    for
      fromKey <- persistentKey(from, "total-map source")
      toKey <- persistentKey(to, "total-map target")
      _ <- requireKey("total-map source", record.from, fromKey)
      _ <- requireKey("total-map target", record.to, toKey)
      mapping <-
        TotalMap
          .fromTargetOrdinals(from, to, record.targets)
          .left
          .map(PersistenceError.InvalidTotalMap.apply)
    yield mapping

  def record[X, Y](
      mapping: PartialMap[X, Y]
  ): Either[PersistenceError, PartialMapRecord] =
    for
      fromKey <- persistentKey(mapping.from, "partial-map source")
      toKey <- persistentKey(mapping.to, "partial-map target")
    yield PartialMapRecord(
      fromKey,
      toKey,
      mapping.optionalTargetOrdinals
    )

  def restore[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      record: PartialMapRecord
  ): Either[PersistenceError, PartialMap[X, Y]] =
    for
      fromKey <- persistentKey(from, "partial-map source")
      toKey <- persistentKey(to, "partial-map target")
      _ <- requireKey("partial-map source", record.from, fromKey)
      _ <- requireKey("partial-map target", record.to, toKey)
      mapping <-
        PartialMap
          .fromOptionalTargetOrdinals(from, to, record.targets)
          .left
          .map(PersistenceError.InvalidPartialMap.apply)
    yield mapping

  def record[X, Y](
      relation: Relation[X, Y]
  ): Either[PersistenceError, RelationRecord] =
    for
      fromKey <- persistentKey(relation.from, "relation source")
      toKey <- persistentKey(relation.to, "relation target")
    yield
      val encoding =
        if relation.isEmpty then RelationEncoding.Empty
        else
          val csr = relation.csr
          RelationEncoding.Csr(
            csr.rowOffsets.toVector,
            csr.targets.toVector
          )
      RelationRecord(fromKey, toKey, encoding)

  def restore[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      record: RelationRecord
  ): Either[PersistenceError, Relation[X, Y]] =
    for
      fromKey <- persistentKey(from, "relation source")
      toKey <- persistentKey(to, "relation target")
      _ <- requireKey("relation source", record.from, fromKey)
      _ <- requireKey("relation target", record.to, toKey)
      relation <-
        record.encoding match
          case RelationEncoding.Empty =>
            Right(Relation.empty(from, to))
          case RelationEncoding.Csr(offsets, targets) =>
            Relation
              .fromCsr(from, to, offsets, targets)
              .left
              .map(PersistenceError.InvalidRelation.apply)
    yield relation

  private def persistentKey(
      domain: FiniteDomain[?],
      role: String
  ): Either[PersistenceError, DomainKey] =
    domain.persistentKey.toRight(
      PersistenceError.EphemeralDomain(role, domain.descriptor)
    )

  private def requireKey(
      role: String,
      expected: DomainKey,
      actual: DomainKey
  ): Either[PersistenceError, Unit] =
    if expected == actual then Right(())
    else
      Left(
        PersistenceError.DomainKeyMismatch(
          role,
          expected,
          actual
        )
      )
