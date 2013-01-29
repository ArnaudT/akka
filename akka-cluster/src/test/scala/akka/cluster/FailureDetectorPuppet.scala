/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.cluster

import akka.remote.testkit.MultiNodeConfig
import akka.remote.FailureDetector
import com.typesafe.config.Config

/**
 * User controllable "puppet" failure detector.
 */
class FailureDetectorPuppet(config: Config) extends FailureDetector {

  trait Status
  object Up extends Status
  object Down extends Status
  object Unknown extends Status

  @volatile private var status: Status = Unknown

  def markNodeAsUnavailable(): Unit =
    status = Down

  def markNodeAsAvailable(): Unit =
    status = Up

  override def isAvailable: Boolean = status match {
    case Unknown | Up ⇒ true
    case Down         ⇒ false
  }

  override def isMonitoring: Boolean = status != Unknown

  override def heartbeat(): Unit =
    if (status == Unknown)
      status = Up

}

