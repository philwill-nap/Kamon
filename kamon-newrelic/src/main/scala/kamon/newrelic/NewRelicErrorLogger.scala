/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.newrelic

import java.util

import akka.actor.{ Actor, ActorLogging }
import akka.event.Logging.{ Error, InitializeLogger, LoggerInitialized }
import com.newrelic.api.agent.{ NewRelic ⇒ NR }
import kamon.trace.{ Tracer, TraceContextAware }

trait CustomParamsSupport {
  this: NewRelicErrorLogger ⇒

  def customParams: Map[String, String]
}

class NewRelicErrorLogger extends Actor with ActorLogging with CustomParamsSupport {
  override def customParams: Map[String, String] = Map.empty

  def receive = {
    case InitializeLogger(_)                                ⇒ sender ! LoggerInitialized
    case error @ Error(cause, logSource, logClass, message) ⇒ notifyError(error)
    case anythingElse                                       ⇒
  }

  def notifyError(error: Error): Unit = runInFakeTransaction {
    val params = new util.HashMap[String, String]()

    if (error.isInstanceOf[TraceContextAware]) {
      val ctx = error.asInstanceOf[TraceContextAware].traceContext
      params put ("TraceToken", ctx.token)
    }

    customParams foreach { case (k, v) ⇒ params.put(k, v) }

    if (error.cause == Error.NoCause) NR.noticeError(error.message.toString, params)
    else NR.noticeError(error.cause, params)
  }

  //Really ugly, but temporal hack until next release...
  def runInFakeTransaction[T](thunk: ⇒ T): T = {
    val oldName = Thread.currentThread.getName
    Thread.currentThread.setName(Tracer.currentContext.name)
    try thunk finally Thread.currentThread.setName(oldName)
  }
}