package net.glorat.cqrs

case class CommitedEvent(event:DomainEvent, streamId: GUID, streamRevision:Int)
