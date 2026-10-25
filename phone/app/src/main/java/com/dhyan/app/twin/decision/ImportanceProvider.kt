package com.dhyan.app.twin.decision

fun interface ImportanceProvider { fun score(facts:NotificationFacts):Double }
/** Reads the real 4B model only. Feedback must be serialized externally for stable evaluation. */
class ModelImportanceProvider(private val model:ImportanceModel):ImportanceProvider {
    override fun score(facts:NotificationFacts):Double = model.score(senderHash=facts.senderHash,appCat=facts.appCat,
        notifCat=facts.notifCat,isVip=facts.isVip,hasReplyAction=facts.hasReplyAction,repeatCount5m=facts.repeatCount5m,
        rawTextHint=null,urgencyScore=facts.urgencyScore)
}
