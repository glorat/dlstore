package net.glorat.ledger

import java.io.{ByteArrayInputStream, ObjectInputStream}

/**
  * Created by kevin on 25/4/2018.
  */
object BinarySerializer {
  import java.io.{ByteArrayOutputStream, IOException, ObjectOutputStream}

  val serializer = (data: Object) => {
    try {
      val baos = new ByteArrayOutputStream
      val oos = new ObjectOutputStream(baos)
      oos.writeObject(data)
      oos.close()
      val b = baos.toByteArray
      b
    } catch {
      case e: IOException =>
        new Array[Byte](0)
    }
  }
  val deserializer = (bytes: Array[Byte]) => {
    val bais = new ByteArrayInputStream(bytes)
    val ois = new ObjectInputStream(bais)
    val obj = ois.readObject()
    ois.close()
    obj
  }

}
