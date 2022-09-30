package com.tudux.taxi.http

import akka.pattern.ask
import akka.http.scaladsl.server.Directives._
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.tudux.taxi.actors.{TaxiCostStat, TaxiExtraInfoStat, TaxiStat, TaxiTripPassengerInfoStat, TaxiTripTimeInfoStat}
import com.tudux.taxi.actors.TaxiStatCommand.{CreateTaxiStat, DeleteTaxiStat}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.tudux.taxi.actors.TaxiCostStatCommand.{DeleteTaxiCostStat, GetTaxiCostStat, UpdateTaxiCostStat}
import com.tudux.taxi.actors.TaxiExtraInfoStatCommand.{GetTaxiExtraInfoStat, UpdateTaxiExtraInfoStat}
import com.tudux.taxi.actors.TaxiStatResponseResponses.TaxiStatCreatedResponse
import com.tudux.taxi.actors.TaxiTripPassengerInfoStatCommand.{GetTaxiPassengerInfoStat, UpdateTaxiPassenger}
import com.tudux.taxi.actors.TaxiTripTimeInfoStatCommand.{GetTaxiTimeInfoStat, UpdateTaxiTripTimeInfoStat}
import spray.json._

import scala.concurrent.ExecutionContext
//import como.tudux.bank.actors.PersistentBankAccount.{Command, Response}
//import como.tudux.bank.actors.PersistentBankAccount.Command._
//import como.tudux.bank.actors.PersistentBankAccount.Response.{BankAccountBalanceUpdatedResponse, BankAccountCreatedResponse, GetBankAccountResponse}
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

case class CreateTaxiStatRequest(VendorID: Int, tpep_pickup_datetime: String, tpep_dropoff_datetime: String, passenger_count: Int,
                                 trip_distance: Double, pickup_longitude: Double, pickup_latitude: Double, RateCodeID: Int,
                                 store_and_fwd_flag: String, dropoff_longitude: Double, dropoff_latitude: Double,
                                 payment_type: Int, fare_amount: Double, extra: Double, mta_tax: Double,
                                 tip_amount: Double, tolls_amount: Double, improvement_surcharge: Double, total_amount: Double) {
  def toCommand: CreateTaxiStat = CreateTaxiStat(TaxiStat(VendorID, tpep_pickup_datetime, tpep_dropoff_datetime, passenger_count,
    trip_distance, pickup_longitude, pickup_latitude, RateCodeID,
    store_and_fwd_flag, dropoff_longitude, dropoff_latitude,
    payment_type, fare_amount, extra, mta_tax,
    tip_amount, tolls_amount, improvement_surcharge, total_amount))
}

case class UpdatePassengerInfoRequest(passenger_count: Int) {
  def toCommand(statId: String) : UpdateTaxiPassenger = UpdateTaxiPassenger(statId,TaxiTripPassengerInfoStat(passenger_count))
}
case class UpdateExtraInfoRequest(pickup_longitude: Double, pickup_latitude: Double, RateCodeID: Int,
                                  store_and_fwd_flag: String, dropoff_longitude: Double, dropoff_latitude: Double) {
  def toCommand(statId: String) : UpdateTaxiExtraInfoStat = UpdateTaxiExtraInfoStat(statId,TaxiExtraInfoStat(pickup_longitude,pickup_latitude, RateCodeID,
    store_and_fwd_flag,dropoff_longitude,dropoff_latitude))
}
case class UpdateTimeInfoRequest(tpep_pickup_datetime: String,tpep_dropoff_datetime: String) {
  def toCommand(statId: String) : UpdateTaxiTripTimeInfoStat = UpdateTaxiTripTimeInfoStat(statId,TaxiTripTimeInfoStat(tpep_pickup_datetime,tpep_dropoff_datetime))
}
case class UpdateCostInfoRequest(VendorID: Int,
                                 trip_distance: Double,
                                 payment_type: Int, fare_amount: Double, extra: Double, mta_tax: Double,
                                 tip_amount: Double, tolls_amount: Double, improvement_surcharge: Double, total_amount: Double) {
  def toCommand(statId: String) : UpdateTaxiCostStat = UpdateTaxiCostStat(statId,TaxiCostStat(VendorID,
    trip_distance,
    payment_type, fare_amount, extra, mta_tax,
    tip_amount, tolls_amount, improvement_surcharge, total_amount))
}


trait TaxiCostStatProtocol extends DefaultJsonProtocol {
  implicit val taxiCostStatFormat = jsonFormat11(TaxiCostStat)
}

trait TaxiTimeInfoStatProtocol extends DefaultJsonProtocol {
  implicit val taxiTimeInfoFormat = jsonFormat3(TaxiTripTimeInfoStat)
}

trait TaxiPassengerInfoProtocol extends DefaultJsonProtocol {
  implicit val taxiPassengerFormat = jsonFormat2(TaxiTripPassengerInfoStat)
}

trait TaxiExtraInfoProtocol extends DefaultJsonProtocol {
  implicit val taxiExtraInfoFormat = jsonFormat7(TaxiExtraInfoStat)
}


class TaxiStatsRouter(taxiTripActor: ActorRef)(implicit system: ActorSystem) extends SprayJsonSupport
  with TaxiCostStatProtocol
  with TaxiTimeInfoStatProtocol
  with TaxiPassengerInfoProtocol
  with TaxiExtraInfoProtocol
  {
  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val routes: Route = {
    pathPrefix("api" / "yellowtaxi" / "stat") {
      get {
        complete(StatusCodes.OK)
      } ~
        post {
            entity(as[CreateTaxiStatRequest]) { request =>
              val statCreatedFuture: Future[TaxiStatCreatedResponse] = (taxiTripActor ? request.toCommand).mapTo[TaxiStatCreatedResponse]
              println(s"Received http post to create stat $request")
              complete(statCreatedFuture.map { r =>
                HttpResponse(
                  StatusCodes.Created,
                  entity = HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    s"Stat created with Id ${r.statId.toString}"
                  )
                )
              })
            }
        } ~
        delete {
          path(Segment) { statId =>
            taxiTripActor ! DeleteTaxiStat(statId)
            complete(StatusCodes.OK)
          }
        }
    } ~
    pathPrefix("api" / "yellowtaxi" / "cost") {
      get {
        path(Segment) { statId =>
          println(s"Received some statID $statId")
          complete(
            (taxiTripActor ? GetTaxiCostStat(statId.toString))
              .mapTo[Option[TaxiCostStat]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
      put {
        path(Segment) { statId =>
          put {
            entity(as[UpdateCostInfoRequest]) { request =>
              taxiTripActor ! request.toCommand(statId)
              complete(StatusCodes.OK)
            }
          }
        }
      } ~
      delete {
        path(Segment) { statId =>
              taxiTripActor ! DeleteTaxiCostStat(statId)
              complete(StatusCodes.OK)
        }
      }
    } ~
    pathPrefix("api" / "yellowtaxi" / "time") {
      get {
        path(Segment) { statId =>
          println(s"Received some statID $statId")
          complete(
            (taxiTripActor ? GetTaxiTimeInfoStat(statId.toString))
              .mapTo[Option[TaxiTripTimeInfoStat]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
      put {
        path(Segment) { statId =>
          put {
            entity(as[UpdateTimeInfoRequest]) { request =>
              taxiTripActor ! request.toCommand(statId)
              complete(StatusCodes.OK)
            }
          }
        }
      }
    } ~
    pathPrefix("api" / "yellowtaxi" / "passenger") {
      get {
        path(Segment) { statId =>
          println(s"Received some statID $statId")
          complete(
            (taxiTripActor ? GetTaxiPassengerInfoStat(statId.toString))
              .mapTo[Option[TaxiTripPassengerInfoStat]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
      put {
        path(Segment) { statId =>
          put {
            entity(as[UpdatePassengerInfoRequest]) { request =>
              taxiTripActor ! request.toCommand(statId)
              complete(StatusCodes.OK)
            }
          }
        }
      }
    } ~
    pathPrefix("api" / "yellowtaxi" / "extrainfo") {
      get {
        path(Segment) { statId =>
          println(s"Received some statID $statId")
          complete(
            (taxiTripActor ? GetTaxiExtraInfoStat(statId.toString))
              .mapTo[Option[TaxiExtraInfoStat]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
      put {
        path(Segment) { statId =>
          put {
            entity(as[UpdateExtraInfoRequest]) { request =>
              taxiTripActor ! request.toCommand(statId)
              complete(StatusCodes.OK)
            }
          }
        }
      }
    }
  }
}
