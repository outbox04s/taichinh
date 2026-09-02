package vn.personalfinance.domain

import java.time.LocalDate
import kotlin.math.roundToLong

data class DebtPaymentSplit(val installmentApplied:Long,val fee:Long,val interest:Long,val principal:Long,val advancePrincipal:Long)
data class ScheduleAmounts(val principal:Long,val interest:Long,val total:Long)

object DebtCalculator{
 fun splitPayment(amount:Long,remainingDue:Long,remainingFee:Long,remainingInterest:Long,currentPrincipal:Long,allowAdvance:Boolean):DebtPaymentSplit{
  require(amount>0);require(remainingDue>=0);require(amount<=remainingDue||allowAdvance)
  val applied=minOf(amount,remainingDue);val fee=minOf(applied,remainingFee);val interest=minOf(applied-fee,remainingInterest);val principal=applied-fee-interest;val advance=amount-applied
  require(principal+advance<=currentPrincipal)
  return DebtPaymentSplit(applied,fee,interest,principal,advance)
 }
 fun installment(remainingPrincipal:Long,expectedPayment:Long,interestRatePercent:Double,interestType:String,frequency:String):ScheduleAmounts{
  val divisor=when(frequency){"weekly"->if(interestType=="yearly")52.0 else 4.345;"quarterly"->if(interestType=="yearly")4.0 else 1.0/3.0;"yearly"->if(interestType=="yearly")1.0 else 1.0/12.0;else->if(interestType=="yearly")12.0 else 1.0}
  val interest=if(interestType=="none")0 else (remainingPrincipal*(interestRatePercent/100)/divisor).roundToLong();val principal=minOf(remainingPrincipal,expectedPayment-interest);require(principal>0);return ScheduleAmounts(principal,interest,principal+interest)
 }
 fun installmentStatus(due:LocalDate,paid:Long,total:Long,today:LocalDate)=when{paid>=total->"paid";paid>0&&due<today->"overdue";paid>0->"partially_paid";due<today->"overdue";else->"upcoming"}
 fun restoredPrincipal(current:Long,split:DebtPaymentSplit)=current+split.principal+split.advancePrincipal
 fun canSettle(currentPrincipal:Long)=currentPrincipal==0L
}
