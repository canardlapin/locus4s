addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")
addSbtPlugin("org.typelevel" % "sbt-typelevel-site" % "0.8.7")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")
addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "3.2.1")
addSbtPlugin("ch.epfl.scala" % "sbt-tasty-mima" % "1.4.0")

// is-terminal 0.1.1 contains a malformed Java 22 multi-release class. The
// corrected release keeps cold Scalafmt resolution usable on maintained JDKs.
dependencyOverrides += "io.github.alexarchambault" % "is-terminal" % "0.1.2"
