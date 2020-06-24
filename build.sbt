name := "dlstore-root"

version := "0.2"

organization := "net.glorat"

scalaVersion := "2.12.10"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val root = Project(id = "dlstore-root",
  base = file(".")) aggregate(dlstore, playstore)

lazy val dlstore = project

lazy val playstore = project.dependsOn(dlstore % "compile->compile;compile->test")
  .enablePlugins(PlayScala)
  .enablePlugins(SbtTwirl)
