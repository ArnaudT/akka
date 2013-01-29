/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package docs.channels

import akka.testkit.AkkaSpec
import akka.channels._

class ChannelDocSpec extends AkkaSpec {
  
  trait A
  trait B
  trait C
  trait D
  
  "demonstrate why Typed Channels" in {
    //#motivation0
    trait Request
    case class Command(msg: String) extends Request
    
    trait Reply
    case object CommandSuccess extends Reply
    case class CommandFailure(msg: String) extends Reply
    //#motivation0
    
    def someActor = testActor
    //#motivation1
    val requestProcessor = someActor
    requestProcessor ! Command
    //#motivation1
    expectMsg(Command)

    /*
    //#motivation2
    val requestProcessor = new ChannelRef[(Request, Reply) :+: TNil](someActor)
    requestProcessor <-!- Command
    //#motivation2
     */
    
    type Example =
      //#motivation-types
      (A, B) :+: (C, D) :+: TNil
      //#motivation-types
  }

}