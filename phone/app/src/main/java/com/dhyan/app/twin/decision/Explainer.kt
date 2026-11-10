package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.*
import java.util.Locale

class Explainer {
    fun allow(reason:String,safety:String?=null):String="Allowed: "+when {
        safety=="vip" -> "a contact you marked important."
        safety!=null -> "a call, alarm, code or configured safety rule."
        reason=="mode_off" -> "Hunch is off."
        reason=="kill_switch" -> "kill switch is on."
        reason=="high_importance" -> "high importance."
        reason=="low_evidence" -> "not enough evidence yet."
        reason=="cost_selected" -> "showing has the lower expected cost."
        reason=="explored" -> "a randomized exploration choice."
        else -> "not enough information."
    }
    fun factors(p:LossPrediction.Ready,snapshot:ContextSnapshot,importance:Double,kappa:Double,tau:Double):List<Factor> {
        fun n(x:Double)=String.format(Locale.ROOT,"%.1f",x)
        val delta=p.eShowMin-p.eSuppMin
        val fatigue=when(snapshot.fatigueBand){"low"->1.0;"med"->1.2;"high"->1.5;else->null}
        val candidates=listOfNotNull(
            Factor("loss","about ${n(delta)} min of focus lost (80% simulation interval ${n(p.deltaP10)} to ${n(p.deltaP90)})",delta),
            Factor("importance","low importance",kappa*(tau/60.0)*(1-importance*importance)),
            p.pPickupMean?.takeIf{it.isFinite()&&it in 0.0..1.0}?.let{Factor("pickup","you open this about ${n(it*10)} of 10 times in this context",delta*it)},
            fatigue?.let{Factor("fatigue","${snapshot.fatigueBand} fatigue, so resuming takes longer",delta*(1-1/it))},
            if(snapshot.minutesInState.isFinite()&&snapshot.minutesInState>=0)Factor("laptop","laptop is in ${snapshot.laptopState.wire} for ${n(snapshot.minutesInState)} min",
                if(snapshot.laptopState==LaptopState.FLOWING || snapshot.focusState==FocusState.DEEP_FOCUS).25*delta else 0.0) else null)
        return candidates.withIndex().sortedWith(compareByDescending<IndexedValue<Factor>> {it.value.contribution}.thenBy{it.index}).take(2).map{it.value}
    }
    fun held(factors:List<Factor>):String="Held: "+factors.joinToString("; "){it.text}+". Tap to show now."
}
