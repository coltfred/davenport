scalaVersion := "2.11.8"

// crossScalaVersions := Seq("2.10.4")

resolvers ++= Seq(
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/release/",
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
  "Oncue Bintray Repo" at "http://dl.bintray.com/oncue/releases"
)

// Production
libraryDependencies ++= Seq(
  "org.typelevel" %% "cats" % "0.8.1",
  "co.fs2" %% "fs2-core" % "0.9.2",
  "co.fs2" %% "fs2-cats" % "0.2.0",
  "com.couchbase.client" % "core-io" % "1.2.3",
  "io.reactivex" %% "rxscala" % "0.25.0", // to better work with the couchbase java client
  "io.argonaut" %% "argonaut-cats" % "6.2-RC2",
  "org.scodec" %% "scodec-bits" % "1.1.2"
)

// Test
libraryDependencies ++= Seq(
  "com.ironcorelabs" %% "cats-scalatest" % "2.1.1",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
  "com.github.melrief" %% "pureconfig" % "0.4.0" % "test"
)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")

// Code coverage checks
coverageMinimum := 70
coverageFailOnMinimum := true
coverageHighlighting := scalaBinaryVersion.value == "2.11"

tutSettings
unidocSettings
site.settings
ghpages.settings
Lint.settings
site.includeScaladoc()
releaseVersionBump := sbtrelease.Version.Bump.Bugfix
com.typesafe.sbt.site.JekyllSupport.requiredGems := Map(
  "jekyll" -> "2.4.0",
  "kramdown" -> "1.5.0",
  "jemoji" -> "0.4.0",
  "jekyll-sass-converter" -> "1.2.0",
  "jekyll-mentions" -> "0.2.1"
)
site.jekyllSupport()
// Enable this if you're convinced every publish should update docs
// site.publishSite

tutSourceDirectory := sourceDirectory.value / "tutsrc"
tutTargetDirectory := sourceDirectory.value / "jekyll" / "_tutorials"

git.remoteRepo := "git@github.com:IronCoreLabs/davenport.git"

releasePublishArtifactsAction := PgpKeys.publishSigned.value

// Apply default Scalariform formatting.
// Reformat at every compile.
// c.f. https://github.com/sbt/sbt-scalariform#advanced-configuration for more options.
scalariformSettings

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8", // yes, this is 2 args
  "-feature",
  "-unchecked",
  "-language:higherKinds",
  "-Xfatal-warnings",
  // "-Xlog-implicits",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)
