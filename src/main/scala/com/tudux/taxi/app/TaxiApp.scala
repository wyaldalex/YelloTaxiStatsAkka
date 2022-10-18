package com.tudux.taxi.app

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import com.tudux.taxi.actors.TaxiTripActor
import com.tudux.taxi.http.routes.MainRouter
import com.tudux.taxi.http.swagger.Swagger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

//Visit Swagger Documentation: http://localhost:10001/swagger-ui/index.html
object TaxiApp extends App {

  def startHttpServer(taxiAppActor: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val scheduler: ExecutionContext = system.dispatcher

    val router = new MainRouter(taxiAppActor)
    // if the sub domain parent actors need to be accessed directly then , the actor reference has to be provided here
    // for the corresponding routes, who will monitor those parents? if they are created here then there is no parent actor
    val routes = router.routes  ~ Swagger(system).routes ~ getFromResourceDirectory("swagger-ui")

    val bindingFuture = Http().newServerAt("localhost", 10001).bind(routes)

    bindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")

      case Failure(exception) =>
        system.log.error(s"Failed to bing HTTP server, because: $exception")
        system.terminate()
    }
  }

  implicit val system: ActorSystem = ActorSystem("TaxiStatsAppSystem")
  implicit val timeout: Timeout = Timeout(2.seconds)

  val taxiAppActor = system.actorOf(TaxiTripActor.props, "taxiParentAppActor")

  startHttpServer(taxiAppActor)

}
