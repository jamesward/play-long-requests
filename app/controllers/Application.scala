package controllers

import java.util.UUID
import javax.inject.Inject

import actors.{GetJobResult, LongJob}
import akka.actor.{ActorNotFound, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Enumerator
import play.api.mvc._

import scala.concurrent.{TimeoutException, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Application @Inject() (actorSystem: ActorSystem) extends Controller {

  def chunker = Action {
    val actorRef = actorSystem.actorOf(Props[LongJob])
    val futureResult = actorRef.ask(GetJobResult)(Timeout(2.minutes)).mapTo[String]
    futureResult.onComplete(_ => actorSystem.stop(actorRef)) // stop the actor

    val enumerator = Enumerator.generateM {
      // output spaces until the future is complete
      if (futureResult.isCompleted) Future.successful(None)
      else Promise.timeout(Some(" "), 5.seconds)
    } andThen {
      // return the result
      Enumerator.flatten(futureResult.map(Enumerator(_)))
    }

    Ok.chunked(enumerator)
  }

  def redir(maybeId: Option[String]) = Action.async {

    val (actorRefFuture, id) = maybeId.fold {
      // no id so create a job
      val id = UUID.randomUUID().toString
      (Future.successful(actorSystem.actorOf(Props[LongJob], id)), id)
    } { id =>
      (actorSystem.actorSelection(s"user/$id").resolveOne(1.second), id)
    }

    actorRefFuture.flatMap { actorRef =>
      actorRef.ask(GetJobResult)(Timeout(25.seconds)).mapTo[String].map { result =>
        // received the result
        actorSystem.stop(actorRef)
        Ok(result)
      } recover {
        // did not receive the result in time so redirect
        case e: TimeoutException => Redirect(routes.Application.redir(Some(id)))
      }
    } recover {
      // did not find the actor specified by the id
      case e: ActorNotFound => InternalServerError("Result no longer available")
    }

  }

}
