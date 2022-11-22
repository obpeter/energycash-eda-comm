package at.energydash
package actor

object SchedulerName extends Enumeration {
  type SchedulerName = Value

  val Every3hours, Every10Seconds, Every1Minute, Notify, HealthCheck = Value
}
