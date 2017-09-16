package org.apollo.game.action

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.selects.select

class AsyncActionRunner(val actionSupplier: () -> Action<*>, val callback: suspend () -> Unit) {
    var job: Job? = null
    var pulseChannel = Channel<Int>(1)
    var unsentPulses = 0

    fun pulse() {
        if (pulseChannel.offer(unsentPulses + 1)) {
            unsentPulses = 0
        } else {
            unsentPulses++
        }
    }

    fun start() {
        if (job != null) {
            return
        }

        val action = actionSupplier.invoke()

        job = launch(CommonPool) {
            select {
                pulseChannel.onReceive {
                    callback()
                    action.stop()
                }
            }
        }
    }

    fun started(): Boolean {
        return job != null
    }

    fun stop() {
        job?.cancel()
        pulseChannel.close()
    }

    suspend fun wait(pulses: Int = 1) {
        var remainingPulses = pulses

        while (remainingPulses > 0) {
            val numPulses = pulseChannel.receive()
            remainingPulses -= numPulses
        }
    }

    suspend fun await(condition: () -> Boolean, timeout: Int = 15) {
        var remainingPulsesBeforeTimeout = timeout

        while (!condition.invoke()) {
            remainingPulsesBeforeTimeout -= pulseChannel.receive()
            if (remainingPulsesBeforeTimeout <= 0) {
                break
            }
        }
    }
}