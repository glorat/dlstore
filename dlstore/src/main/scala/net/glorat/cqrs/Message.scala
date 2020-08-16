package net.glorat.cqrs

// All these should be immutable value objects so that we can safely serialize etc.
trait Message extends Product // Product encourages use of case class
trait Command extends Message

trait DomainEvent extends Message
