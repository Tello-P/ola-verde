package es.olaverde.core.estimation

import es.olaverde.core.model.SignalObservation

/** Agrupa observaciones por semáforo y franja y mantiene un modelo para cada par. */
class SignalModelStore(private val estimator: CycleEstimator = CycleEstimator()) {
    private val observations = mutableMapOf<Pair<String, TimeBand>, MutableList<SignalObservation>>()
    private val models = mutableMapOf<Pair<String, TimeBand>, SignalModel>()

    fun add(obs: List<SignalObservation>) {
        val touched = mutableSetOf<Pair<String, TimeBand>>()
        for (o in obs) {
            val key = o.signalId to TimeBand.of(timeOf(o))
            observations.getOrPut(key) { mutableListOf() } += o
            touched += key
        }
        for (key in touched) {
            estimator.estimate(key.first, observations.getValue(key))?.let { models[key] = it }
        }
    }

    fun model(signalId: String, tMillis: Long): SignalModel? = models[signalId to TimeBand.of(tMillis)]

    private fun timeOf(o: SignalObservation) = when (o) {
        is SignalObservation.GreenStart -> o.tMillis
        is SignalObservation.GreenAt -> o.tMillis
        is SignalObservation.RedInterval -> o.fromMillis
    }
}
