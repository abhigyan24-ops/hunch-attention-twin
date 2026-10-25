package com.dhyan.app.twin.decision

/** SplitMix64 for policy/bootstrap sampling; independent of the twin's simulation streams. */
class PolicyRng(seed:Long) {
    private var state=seed
    fun nextDouble():Double {state+=-7046029254386353131L;return (mix(state) ushr 11).toDouble()/9007199254740992.0}
    fun nextInt(bound:Int):Int {require(bound>0);return (nextDouble()*bound).toInt()}
    companion object {
        fun mix(input:Long):Long {var z=input;z=(z xor (z ushr 30))*-4658895280553007687L;z=(z xor (z ushr 27))*-7723592293110705685L;return z xor (z ushr 31)}
        fun notificationSeed(seed:Long,key:String,postedTsMs:Long):Long {
            var hash=-3750763034362895579L
            for(c in key)hash=(hash xor c.code.toLong())*1099511628211L
            return mix(seed xor mix(hash) xor mix(postedTsMs))
        }
    }
}
data class ExplorationChoice(val action:Action,val explored:Boolean,val propensity:Double,val eligibleActions:Set<Action>)
class ExplorationPolicy {
    fun choose(costAction:Action,facts:NotificationFacts,settings:PolicySettings,importance:Double,deltaL:Double,
        failedOpen:Boolean=false,neverHold:Boolean=false):ExplorationChoice {
        require(settings.epsilon.isFinite() && settings.epsilon in 0.0..1.0)
        val eligible=settings.explorationEnabled && !settings.strictSession && !failedOpen && settings.mode!=PolicyMode.OFF &&
            !settings.killSwitch && !neverHold && !facts.isVip && importance<.4 && deltaL>0 && costAction!=Action.ALLOW
        if(!eligible)return ExplorationChoice(costAction,false,1.0,setOf(costAction))
        val eps=settings.epsilon
        val explored=PolicyRng(PolicyRng.notificationSeed(settings.seed,facts.keyHash,facts.postedTsMs)).nextDouble()<eps
        val action=if(explored)Action.ALLOW else costAction
        val support=when(eps){0.0->setOf(costAction);1.0->setOf(Action.ALLOW);else->setOf(costAction,Action.ALLOW)}
        return ExplorationChoice(action,explored,if(explored)eps else 1-eps,support)
    }
}
