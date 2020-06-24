package net.glorat.cqrs

case class CommittedEvent(event:DomainEvent, streamId: GUID, streamRevision:Int)
