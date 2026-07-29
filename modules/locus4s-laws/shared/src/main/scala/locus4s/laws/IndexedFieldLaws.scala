package locus4s.laws

import locus4s.data.IndexedField

object IndexedFieldLaws:
  def mapIdentity[S, A](
      field: IndexedField[S, A]
  )(
      equal: (A, A) => Boolean
  ): Boolean =
    field.valuesInDomainOrder
      .zip(field.map(identity).valuesInDomainOrder)
      .forall((left, right) => equal(left, right))

  def mapComposition[S, A, B, C](
      field: IndexedField[S, A],
      first: A => B,
      second: B => C
  )(
      equal: (C, C) => Boolean
  ): Boolean =
    field
      .map(first)
      .map(second)
      .valuesInDomainOrder
      .zip(field.map(first.andThen(second)).valuesInDomainOrder)
      .forall((left, right) => equal(left, right))
