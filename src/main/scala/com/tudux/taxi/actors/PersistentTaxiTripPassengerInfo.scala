package com.tudux.taxi.actors

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor

case class TaxiTripPassengerInfoStat(passengerCount: Int,deletedFlag: Boolean = false)

sealed trait TaxiTripPassengerInfoCommand
object TaxiTripPassengerInfoStatCommand {
  case class CreateTaxiTripPassengerInfoStat(statId: String,taxiTripPassengerInfoStat: TaxiTripPassengerInfoStat) extends TaxiTripPassengerInfoCommand
  case class GetTaxiPassengerInfoStat(statId: String) extends TaxiTripPassengerInfoCommand
  case class UpdateTaxiPassenger(statId: String, taxiTripPassengerInfoStat: TaxiTripPassengerInfoStat) extends TaxiTripPassengerInfoCommand
  case class DeleteTaxiTripPassenger(statId: String) extends TaxiTripPassengerInfoCommand
  case object GetTotalPassengerInfoLoaded
}


sealed trait TaxiTripPassengerInfoEvent
object TaxiTripPassengerInfoStatEvent{
  case class TaxiTripPassengerInfoStatCreatedEvent(statId: String, taxiTripPassengerInfoStat: TaxiTripPassengerInfoStat) extends TaxiTripPassengerInfoEvent
  case class UpdatedTaxiPassengerEvent(statId: String, taxiTripPassengerInfoStat: TaxiTripPassengerInfoStat) extends TaxiTripPassengerInfoEvent
  case class DeletedTaxiTripPassengerEvent(statId: String) extends TaxiTripPassengerInfoEvent
}

object PersistentTaxiTripPassengerInfo {
  def props(id: String): Props = Props(new PersistentTaxiTripPassengerInfo(id))
}
class PersistentTaxiTripPassengerInfo(id: String) extends PersistentActor with ActorLogging {

  import TaxiTripPassengerInfoStatCommand._
  import TaxiTripPassengerInfoStatEvent._

  //Persistent Actor State
  var statCounter: Int = 1
  var taxiTripPassengerInfoStatMap : Map[String,TaxiTripPassengerInfoStat] = Map.empty

  override def persistenceId: String = id

  override def receiveCommand: Receive = {
    case CreateTaxiTripPassengerInfoStat(statId,taxiTripPassengerInfoStat) =>
      persist(TaxiTripPassengerInfoStatCreatedEvent(statId,taxiTripPassengerInfoStat)) { _ =>
        log.info(s"Creating Passenger Info Stat $taxiTripPassengerInfoStat")
        taxiTripPassengerInfoStatMap = taxiTripPassengerInfoStatMap + (statId -> taxiTripPassengerInfoStat)
        statCounter += 1
      }
    case GetTaxiPassengerInfoStat(statId) =>
      sender() ! taxiTripPassengerInfoStatMap.get(statId)
    case UpdateTaxiPassenger(statId,taxiTripPassengerInfoStat) =>
      log.info(s"Applying update for Passenger Info for id $statId")
      if (taxiTripPassengerInfoStatMap.contains(statId)) {
        persist(UpdatedTaxiPassengerEvent(statId, taxiTripPassengerInfoStat)) { _ =>
          taxiTripPassengerInfoStatMap = taxiTripPassengerInfoStatMap + (statId -> taxiTripPassengerInfoStat)
        }
      } else log.info(s"Entry not found to update by id $statId")
    case DeleteTaxiTripPassenger(statId) =>
      log.info("Deleting taxi cost stat")
      if (taxiTripPassengerInfoStatMap.contains(statId)) {
        persist(DeletedTaxiTripPassengerEvent(statId)) { _ =>
          val taxiPassengerInfoDeleted: TaxiTripPassengerInfoStat = taxiTripPassengerInfoStatMap(statId).copy(deletedFlag = true)
          taxiTripPassengerInfoStatMap = taxiTripPassengerInfoStatMap + (statId -> taxiPassengerInfoDeleted)
        }
      }
    case GetTotalPassengerInfoLoaded =>
      sender() ! taxiTripPassengerInfoStatMap.size
    case _ =>
      log.info(s"Received something else at ${self.path.name}")

  }

  override def receiveRecover: Receive = {
    case TaxiTripPassengerInfoStatCreatedEvent(statId,taxiTripPassengerInfoStat) =>
      log.info(s"Recovering Passenger Info Stat $taxiTripPassengerInfoStat")
      taxiTripPassengerInfoStatMap = taxiTripPassengerInfoStatMap + (statId -> taxiTripPassengerInfoStat)
      statCounter += 1
    case   UpdatedTaxiPassengerEvent(statId,taxiTripPassengerInfoStat) =>
      log.info(s"Recovered Update Event applied for Passenger info Id: $statId")
      taxiTripPassengerInfoStatMap = taxiTripPassengerInfoStatMap + (statId -> taxiTripPassengerInfoStat)
    case DeletedTaxiTripPassengerEvent(statId) =>
      log.info(s"Recovered Deleted Event applied for Passenger info Id: $statId")
      val taxiPassengerInfoDeleted: TaxiTripPassengerInfoStat = taxiTripPassengerInfoStatMap(statId).copy(deletedFlag = true)
      taxiTripPassengerInfoStatMap = taxiTripPassengerInfoStatMap + (statId -> taxiPassengerInfoDeleted)
  }
}


