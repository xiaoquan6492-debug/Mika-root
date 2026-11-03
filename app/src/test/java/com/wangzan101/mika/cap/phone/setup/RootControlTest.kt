package com.wangzan101.mika.cap.phone.setup
// Mika-root adaptation notice (2026-11-03): adapted from upstream Mika and/or an upstream reviewed PR;
// see docs/root-fork.md for provenance, attribution, licensing and the no-sole-authorship statement.

import com.wangzan101.mika.core.shell.RootGrant
import com.wangzan101.mika.core.shell.SuAnswer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootControlTest {
    private class Store : RootStore {
        val bools = mutableMapOf<String, Boolean>()
        val strings = mutableMapOf<String, String>()
        override fun getBoolean(key: String, def: Boolean) = bools[key] ?: def
        override fun getString(key: String) = strings[key]
        override fun putBoolean(key: String, value: Boolean) { bools[key] = value }
        override fun putString(key: String, value: String?) {
            if (value == null) strings.remove(key) else strings[key] = value
        }
    }

    private fun kotlinx.coroutines.test.TestScope.control(
        store: Store = Store(),
        probe: suspend (Int) -> SuAnswer? = { null },
    ): RootControl {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return RootControl(store, CoroutineScope(SupervisorJob() + dispatcher), {}, probe, dispatcher) { true }
    }

    private class BlockingStore(
        private val blockCommandsGrant: Boolean = false,
        private val blockStartGrant: Boolean = false,
    ) : RootStore {
        val bools = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val strings = java.util.concurrent.ConcurrentHashMap<String, String>()
        val commit = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun getBoolean(key: String, def: Boolean) = bools[key] ?: def
        override fun getString(key: String) = strings[key]
        override fun putBoolean(key: String, value: Boolean) {
            if (blockCommandsGrant && key == "commands" && value) pause()
            bools[key] = value
        }
        override fun putString(key: String, value: String?) {
            if (blockStartGrant && key == "startAnswer" && value == "Granted") pause()
            if (value == null) strings.remove(key) else strings[key] = value
        }
        private fun pause() {
            commit.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "test release latch timed out" }
        }
    }

    private fun concurrentControl(store: RootStore): RootControl =
        RootControl(store, CoroutineScope(SupervisorJob() + Dispatchers.Default), {}, { SuAnswer.Granted }, Dispatchers.Default) { true }

    @Test
    fun commandsGrantCommitAndDisableAreSerialized() = runBlocking {
        val store = BlockingStore(blockCommandsGrant = true)
        val c = concurrentControl(store)
        c.setCommands(true)
        assertTrue("grant commit did not reach store", store.commit.await(5, TimeUnit.SECONDS))
        val attempted = CountDownLatch(1)
        val done = CountDownLatch(1)
        val revoke = Thread {
            attempted.countDown()
            c.setCommands(false)
            done.countDown()
        }
        revoke.start()
        try {
            assertTrue("revocation did not start", attempted.await(5, TimeUnit.SECONDS))
            assertFalse("revocation must wait for the grant commit", done.await(500, TimeUnit.MILLISECONDS))
        } finally {
            store.release.countDown()
            revoke.join(5_000)
        }
        assertTrue("revocation did not finish", done.await(5, TimeUnit.SECONDS))
        assertFalse(c.state.value.commands)
        assertEquals(false, store.bools["commands"])
    }

    @Test
    fun commandsGrantCommitAndTurnOffAllAreSerialized() = runBlocking {
        val store = BlockingStore(blockCommandsGrant = true)
        val c = concurrentControl(store)
        c.setCommands(true)
        assertTrue("grant commit did not reach store", store.commit.await(5, TimeUnit.SECONDS))
        val attempted = CountDownLatch(1)
        val done = CountDownLatch(1)
        val revoke = Thread {
            attempted.countDown()
            c.turnOffAll()
            done.countDown()
        }
        revoke.start()
        try {
            assertTrue("forget did not start", attempted.await(5, TimeUnit.SECONDS))
            assertFalse("forget must wait for the grant commit", done.await(500, TimeUnit.MILLISECONDS))
        } finally {
            store.release.countDown()
            revoke.join(5_000)
        }
        assertTrue("forget did not finish", done.await(5, TimeUnit.SECONDS))
        assertFalse(c.state.value.commands)
        assertFalse(c.state.value.start)
        assertEquals(false, store.bools["commands"])
        assertEquals(false, store.bools["start"])
        assertFalse(store.strings.containsKey("commandsAnswer"))
    }

    @Test
    fun startGrantCommitAndDisableAreSerialized() = runBlocking {
        val store = BlockingStore(blockStartGrant = true)
        val c = concurrentControl(store)
        c.setStart(true)
        assertTrue("start grant commit did not reach store", store.commit.await(5, TimeUnit.SECONDS))
        val attempted = CountDownLatch(1)
        val done = CountDownLatch(1)
        val revoke = Thread {
            attempted.countDown()
            c.setStart(false)
            done.countDown()
        }
        revoke.start()
        try {
            assertTrue("start revocation did not start", attempted.await(5, TimeUnit.SECONDS))
            assertFalse("start revocation must wait for commit", done.await(500, TimeUnit.MILLISECONDS))
        } finally {
            store.release.countDown()
            revoke.join(5_000)
        }
        assertTrue("start revocation did not finish", done.await(5, TimeUnit.SECONDS))
        assertFalse(c.state.value.start)
        assertEquals(false, store.bools["start"])
    }

    @Test
    fun lateGrantAfterClosingCommandsDoesNotTurnItOn() = runTest {
        val gate = CompletableDeferred<SuAnswer?>()
        val store = Store()
        val c = control(store) { gate.await() }
        c.setCommands(true)
        runCurrent()
        assertEquals(RootGrant.Pending, c.state.value.commandsGrant)
        c.setCommands(false)
        gate.complete(SuAnswer.Granted)
        advanceUntilIdle()
        assertFalse(c.state.value.commands)
        assertNotEquals(RootGrant.Granted, c.state.value.commandsGrant)
        assertEquals(false, store.bools["commands"])
        assertFalse(c.commandsAllowed())
    }

    @Test
    fun turnOffAllIgnoresPendingCommandsAndStartGrants() = runTest {
        val commands = CompletableDeferred<SuAnswer?>()
        val start = CompletableDeferred<SuAnswer?>()
        val store = Store()
        val c = control(store) { uid -> if (uid == 0) commands.await() else start.await() }
        c.setCommands(true)
        c.setStart(true)
        runCurrent()
        c.turnOffAll()
        commands.complete(SuAnswer.Granted)
        start.complete(SuAnswer.Granted)
        advanceUntilIdle()
        assertFalse(c.state.value.commands)
        assertFalse(c.state.value.start)
        assertEquals(RootGrant.Unknown, c.state.value.commandsGrant)
        assertEquals(RootGrant.Unknown, c.state.value.startGrant)
        assertEquals(false, store.bools["commands"])
        assertEquals(false, store.bools["start"])
        assertFalse(c.commandsAllowed())
    }

    @Test
    fun lateGrantAfterClosingStartDoesNotTurnItOn() = runTest {
        val gate = CompletableDeferred<SuAnswer?>()
        val store = Store()
        val c = control(store) { gate.await() }
        c.setStart(true)
        runCurrent()
        c.setStart(false)
        gate.complete(SuAnswer.Granted)
        advanceUntilIdle()
        assertFalse(c.state.value.start)
        assertNotEquals(RootGrant.Granted, c.state.value.startGrant)
        assertEquals(false, store.bools["start"])
    }

    @Test
    fun oldStartRequestCannotOverwriteANewerRetry() = runTest {
        val first = CompletableDeferred<SuAnswer?>()
        val second = CompletableDeferred<SuAnswer?>()
        var calls = 0
        val c = control { if (++calls == 1) first.await() else second.await() }
        c.setStart(true)
        runCurrent()
        c.retryStart()
        runCurrent()
        first.complete(SuAnswer.Granted)
        advanceUntilIdle()
        assertTrue(c.state.value.start)
        assertEquals(RootGrant.Pending, c.state.value.startGrant)
        second.complete(SuAnswer.NoAnswer)
        advanceUntilIdle()
        assertEquals(RootGrant.NoAnswer, c.state.value.startGrant)
    }

    @Test
    fun oldRequestCannotOverwriteANewerRequest() = runTest {
        val first = CompletableDeferred<SuAnswer?>()
        val second = CompletableDeferred<SuAnswer?>()
        var calls = 0
        val c = control { if (++calls == 1) first.await() else second.await() }
        c.setCommands(true)
        runCurrent()
        c.setCommands(false)
        c.setCommands(true)
        runCurrent()
        first.complete(SuAnswer.Granted)
        advanceUntilIdle()
        assertFalse(c.state.value.commands)
        assertEquals(RootGrant.Pending, c.state.value.commandsGrant)
        second.complete(SuAnswer.Denied)
        advanceUntilIdle()
        assertFalse(c.state.value.commands)
        assertEquals(RootGrant.Denied, c.state.value.commandsGrant)
    }

    @Test
    fun denialWhileOpeningInvalidatesThePendingGrant() = runTest {
        val gate = CompletableDeferred<SuAnswer?>()
        val c = control { gate.await() }
        c.setCommands(true)
        runCurrent()
        c.commandsDenied(SuAnswer.Denied)
        gate.complete(SuAnswer.Granted)
        advanceUntilIdle()
        assertFalse(c.state.value.commands)
        assertEquals(RootGrant.Denied, c.state.value.commandsGrant)
    }

    @Test
    fun grantedCommandsAllowOnlyWhileSwitchAndSuRemain() = runTest {
        val store = Store()
        val c = control(store) { SuAnswer.Granted }
        c.setCommands(true)
        advanceUntilIdle()
        assertTrue(c.state.value.commands)
        assertTrue(c.commandsAllowed())
        c.setCommands(false)
        assertFalse(c.commandsAllowed())
    }
}
