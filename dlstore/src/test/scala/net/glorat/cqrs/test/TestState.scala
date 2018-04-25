package net.glorat.cqrs.test

import net.glorat.cqrs._
import net.glorat.cqrs.example._

import org.scalatest.FlatSpec
class TestState extends FlatSpec {
  val id : GUID = java.util.UUID.fromString("9d9814f5-f531-4d80-8722-f61dcc1679b8")

  "Example model" should "do the obvious" in {
    val model = new InventoryItem(id, "sample")
    assert(model.id == id)
    assert(model.getState.id == id)
    assert(model.getState.activated == true)
    model.checkIn(10)
    model.checkIn(20)
    // Nothing to assert from checkIn with read view
    // The validation logic performs no validation on inventory levels
    model.deactivate()
    assert(model.getState.activated == false)

  }

  it should "not allow changing name to empty" in {
    val model = new InventoryItem(id, "sample")
    model.changeName("okay")
    assertThrows[Exception] {
      model.changeName("")
    }
  }

  it should "not allow checking in negative levels" in {
    val model = new InventoryItem(id, "sample")
    model.checkIn(100)
    assertThrows[Exception] {
      model.checkIn(-100)
    }
  }
}