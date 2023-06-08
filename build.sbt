name := """ehri-shexml-converter-ws"""
organization := "eu.ehri-project"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, LauncherJarPlugin)

scalaVersion := "2.13.9"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
libraryDependencies += "com.herminiogarcia" %% "shexml" % "0.3.3"

dependencyOverrides += "org.scala-lang.modules" %% "scala-parser-combinators" % "2.1.1"
dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.11.4"

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "eu.ehri-project.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "eu.ehri-project.binders._"

// Dist options
topLevelDirectory := None

