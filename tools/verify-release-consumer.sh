#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 VERSION MAVEN_REPOSITORY" >&2
  exit 2
fi

version=$1
repository=$2
[[ "$version" != *SNAPSHOT* ]] || { echo "consumer requires an immutable version" >&2; exit 1; }
[[ -d "$repository" ]] || { echo "repository does not exist: $repository" >&2; exit 1; }

repository=$(cd "$repository" && pwd -P)
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
mkdir -p "$scratch/project" "$scratch/jvm/src/main/scala" "$scratch/js/src/main/scala"

cat > "$scratch/project/build.properties" <<'EOF'
sbt.version=1.12.14
EOF
cat > "$scratch/project/plugins.sbt" <<'EOF'
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
EOF
cat > "$scratch/build.sbt" <<EOF
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*

ThisBuild / scalaVersion := "3.7.4"
ThisBuild / resolvers += "locus4s-release" at "file://$repository"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Werror")

lazy val jvm = project.in(file("jvm")).settings(
  libraryDependencies ++= Seq(
    "io.github.canardlapin" %% "locus4s-core" % "$version",
    "io.github.canardlapin" %% "locus4s-data" % "$version",
    "io.github.canardlapin" %% "locus4s-laws" % "$version"
  )
)

lazy val js = project.in(file("js")).enablePlugins(ScalaJSPlugin).settings(
  libraryDependencies ++= Seq(
    "io.github.canardlapin" %%% "locus4s-core" % "$version",
    "io.github.canardlapin" %%% "locus4s-data" % "$version",
    "io.github.canardlapin" %%% "locus4s-laws" % "$version"
  )
)
EOF
cat > "$scratch/jvm/src/main/scala/Main.scala" <<'EOF'
import locus4s.*
import locus4s.data.VectorField
import locus4s.laws.RegionLaws

object Main:
  def main(args: Array[String]): Unit =
    val packed = FiniteDomain.ephemeral("published-jvm-consumer", 3).toOption.get
    val space = packed.value
    val field = VectorField.fromValues(space, Vector(2, 4, 6)).toOption.get
    val even = Region.fromOrdinals(space, Vector(0, 2)).toOption.get
    assert(field.restrict(even).valuesInDomainOrder.toVector == Vector(2, 6))
    assert(RegionLaws.booleanAlgebra(even, even.complement, Region.empty(space)))
    assert(Relation.identity(space).isRight)
    println("published JVM consumer ok")
EOF
cat > "$scratch/js/src/main/scala/Main.scala" <<'EOF'
import locus4s.*
import locus4s.data.VectorField
import locus4s.laws.RegionLaws

object Main:
  def smoke(): Unit =
    val packed = FiniteDomain.ephemeral("published-js-consumer", 3).toOption.get
    val space = packed.value
    val field = VectorField.fromValues(space, Vector(2, 4, 6)).toOption.get
    val even = Region.fromOrdinals(space, Vector(0, 2)).toOption.get
    assert(field.restrict(even).valuesInDomainOrder.toVector == Vector(2, 6))
    assert(RegionLaws.booleanAlgebra(even, even.complement, Region.empty(space)))
    assert(Relation.identity(space).isRight)
EOF

(cd "$scratch" && sbt -batch -no-colors 'jvm / run' 'js / Compile / fullLinkJS')
