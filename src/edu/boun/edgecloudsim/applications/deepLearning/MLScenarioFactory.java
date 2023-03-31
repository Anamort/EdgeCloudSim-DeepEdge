package edu.boun.edgecloudsim.applications.deepLearning;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.DefaultEdgeServerManager;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.DefaultMobileServerManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.mobility.NomadicMobility;
import edu.boun.edgecloudsim.task_generator.IdleActiveLoadGenerator;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.NetworkModel;

public class MLScenarioFactory implements ScenarioFactory {
    private int m_numOfMobileDevice;
    private double m_simulationTime;
    private String m_orchestratorPolicy;
    private String m_simScenario;

    MLScenarioFactory(int numberOfMobileDevice,
                      double simulationTime,
                      String orchestrationPolicy,
                      String simScenario)
    {
        m_orchestratorPolicy = orchestrationPolicy;
        m_numOfMobileDevice = numberOfMobileDevice;
        m_simulationTime = simulationTime;
        m_simScenario = simScenario;
    }


    /**
     * provides abstract Load Generator Model
     */
    @Override
    public LoadGeneratorModel getLoadGeneratorModel() {
        return new IdleActiveLoadGenerator(m_numOfMobileDevice, m_simulationTime, m_simScenario);
    }

    /**
     * provides abstract Edge Orchestrator
     */
    @Override
    public EdgeOrchestrator getEdgeOrchestrator() {
        return new DeepEdgeOrchestrator(m_orchestratorPolicy, m_simScenario);
    }

    /**
     * provides abstract Mobility Model
     */
    @Override
    public MobilityModel getMobilityModel() {
        return new NomadicMobility(m_numOfMobileDevice, m_simulationTime);
    }

    /**
     * provides abstract Network Model
     */
    @Override
    public NetworkModel getNetworkModel() {
        return new NetworkingModel(m_numOfMobileDevice, m_simScenario);
    }

    /**
     * provides abstract Edge Server Model
     */
    @Override
    public EdgeServerManager getEdgeServerManager() {
        return new DefaultEdgeServerManager();
    }

    /**
     * provides abstract Cloud Server Model
     */
    @Override
    public CloudServerManager getCloudServerManager() {
        return new DefaultCloudServerManager();
    }

    /**
     * provides abstract Mobile Server Model
     */
    @Override
    public MobileServerManager getMobileServerManager() {
        return new DefaultMobileServerManager();
    }

    /**
     * provides abstract Mobile Device Manager Model
     */
    @Override
    public MobileDeviceManager getMobileDeviceManager() throws Exception {
        return new DeepMobileDeviceManager();
    }
}
