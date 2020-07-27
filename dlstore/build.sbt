
name := "dlstore"

version := "0.3.1"

organization := "net.glorat"

description := "Ledger state machine framework backed by Kafka distributed ledger store"

scalaVersion := "2.12.10"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += Resolver.bintrayRepo("cakesolutions", "maven")

libraryDependencies ++= Seq(
  "com.github.salat" %% "salat-core" % "1.11.2",
  "joda-time" % "joda-time" % "2.2",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor" % "2.5.3",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.21" % "test",
  "net.cakesolutions" %% "scala-kafka-client" % "1.0.0",
  "net.cakesolutions" %% "scala-kafka-client-testkit" % "1.0.0" % "test",
  "com.google.firebase" % "firebase-admin" % "6.13.0",
  "org.json4s"   %% "json4s-jackson" % "3.6.7"
)

publishMavenStyle := true

pomIncludeRepository := { _ => false }

licenses := Seq("GNU LESSER GENERAL PUBLIC LICENSE" -> url("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt"))

homepage := Some(url("https://github.com/glorat/dlstore"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/glorat/dlstore"),
    "scm:git@github.com:glorat/dlstore.git"
  )
)

developers := List(
  Developer(
    id    = "glorat",
    name  = "Kevin Tam",
    email = "kevin@glorat.net",
    url   = url("https://github.com/glorat")
  )
)

publishTo := sonatypePublishTo.value
