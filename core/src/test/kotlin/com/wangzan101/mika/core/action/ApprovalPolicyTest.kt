package com.wangzan101.mika.core.action
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import com.wangzan101.mika.core.run.Attendance
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.core.run.RunProvenance
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalPolicyTest {
    @Test fun onlyPaymentAsks() {
        for (level in ActionRisk.entries) {
            assertEquals("$level", Approval.Allow, ApprovalPolicy.decide(Risk(level)))
            assertEquals("$level payment", Approval.Ask, ApprovalPolicy.decide(Risk(level, payment = true)))
        }
    }

    @Test fun everyOriginAndAttendance() {
        for (origin in RunOrigin.entries) for (attendance in Attendance.entries) for (level in ActionRisk.entries) {
            val run = RunProvenance(origin, attendance)
            // 不花钱的:谁发起、人在不在都直接做
            assertEquals("$origin $attendance $level", Approval.Allow, ApprovalPolicy.decide(Risk(level), run))
            // 花钱的:只有手机上发起、人在场时问;问不到的(渠道、定时、MCP、子任务、无人值守)不做
            val expected = if (origin == RunOrigin.Ui && attendance != Attendance.Unattended) Approval.Ask else Approval.Deny
            assertEquals("$origin $attendance $level payment", expected, ApprovalPolicy.decide(Risk(level, payment = true), run))
        }
    }

    @Test fun declaredAskAlsoNeedsTheUserAndIsDeniedWhenUnattended() {
        val ask = com.wangzan101.mika.core.gate.ConfirmationRequest(
            com.wangzan101.mika.core.risk.IrrevAction.other, "", "ls /data", null, "root",
        )
        assertEquals(Approval.Ask, ApprovalPolicy.decide(Risk(ActionRisk.Read, ask = ask), RunProvenance(RunOrigin.Ui, Attendance.Present)))
        assertEquals(Approval.Deny, ApprovalPolicy.decide(Risk(ActionRisk.Read, ask = ask), RunProvenance(RunOrigin.Trigger, Attendance.Unattended)))
        assertEquals(Approval.Deny, ApprovalPolicy.decide(Risk(ActionRisk.Read, ask = ask), RunProvenance(RunOrigin.Child, Attendance.Present)))
    }
}
