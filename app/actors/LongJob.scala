package actors

import akka.actor.Actor
import akka.pattern.pipe
import play.api.libs.concurrent.Promise

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class LongJob extends Actor {

  lazy val jobFuture: Future[String] = Promise.timeout("done!", 60.seconds)

  override def receive = {
    case GetJobResult => jobFuture.pipeTo(sender())
  }

}

case object GetJobResult