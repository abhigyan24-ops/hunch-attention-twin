package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class VerdictSheetTest {
    @Test fun tenFormulaDerivedVerdicts() {
        val now=Instant.parse("2026-01-05T10:00:00Z").toEpochMilli()
        val f=DecisionTestData.facts.copy(postedTsMs=now-1000)
        val s=DecisionTestData.snapshot.copy(ts=now)
        data class Case(val id:String,val facts:NotificationFacts,val i:Double,val show:Double,val supp:Double,val expected:Action,
            val context:ContextSnapshot=s,val evidence:Double=.8,val expectedReason:String?=null)
        // Expectations fixed from formulas before running: see DECISION_POLICY.md controlled regimes.
        val rows=listOf(Case("call",f.copy(notifCat="call"),.2,1000.0,1.0,Action.ALLOW,expectedReason="never_hold"),
            Case("otp",f.copy(isOtpLike=true),.2,1000.0,1.0,Action.ALLOW,expectedReason="never_hold"),
            Case("vip_message",f.copy(isVip=true),.2,1000.0,1.0,Action.ALLOW,expectedReason="never_hold"),
            Case("high_importance",f,.85,20.0,1.0,Action.ALLOW,expectedReason="high_importance"),
            Case("social_like",f,.3,20.0,1.0,Action.DIGEST),Case("promo",f.copy(notifCat="promo"),.1,20.0,1.0,Action.SILENT),
            Case("near_break",f,.6,4.0,.5,Action.DELAY,s.copy(focusState=FocusState.BREAK)),
            Case("baseline_dominated",f,.5,1.0,.9,Action.ALLOW),
            Case("low_evidence",f,.6,20.0,1.0,Action.ALLOW,evidence=.1,expectedReason="low_evidence"),
            Case("stale",f,.3,20.0,1.0,Action.ALLOW,s.copy(ts=now-120001),expectedReason="not_ready"))
        println("VERDICT: id | I | Eshow | Esupp | deltaL | J(ALLOW,DELAY,DIGEST,SILENT) diagnostic controls | action | reason | explanation")
        val actions=mutableSetOf<Action>()
        for(row in rows){
            val ready=LossPrediction.Ready(row.show,row.supp,-1.0,40.0,.6,row.evidence,1,"simulated")
            val p=DecisionPipeline(LossPredictor{_,_,_,_->ready},ImportanceProvider{row.i},zone=ZoneId.of("UTC"))
            val d=p.decide(row.facts,row.context,DecisionTestData.settings,now)
            val times=HoldTimeEstimator().estimate(row.context,DecisionTestData.settings,now,ZoneId.of("UTC"))
            val costs=CostFunction.compute(row.show,row.supp,row.i,3.0,false,times.tauReleaseMin,times.tauDigestMin)
            assertEquals(row.id,row.expected,d.action);row.expectedReason?.let{assertEquals(it,d.reasonCode)};actions.add(d.action)
            println("${row.id} | ${row.i} | ${row.show} | ${row.supp} | ${row.show-row.supp} | ${Action.entries.map{costs.cost(it)}} | ${d.action} | ${d.reasonCode} | ${d.explanation}")
        }
        assertEquals(Action.entries.toSet(),actions)
    }
}
