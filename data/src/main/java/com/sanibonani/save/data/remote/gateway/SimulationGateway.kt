package com.sanibonani.save.data.remote.gateway

import com.sanibonani.save.domain.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A unified gateway for all simulation-related operations.
 * This class aggregates specialized simulation modules.
 */
@Singleton
class SimulationGateway @Inject constructor(
    val bank: BankBackendSimulationModule,
    val edgeFunctions: EdgeFunctionGateway // Re-using existing gateway for function-level simulation
) {
    init {
        AppLogger.i("SimulationGateway", "Simulation Gateway initialized and ready for testing.")
    }
}
