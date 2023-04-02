package edu.boun.edgecloudsim.applications.deepLearning;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.boun.edgecloudsim.applications.sample_app4.FCL_definition;
import org.antlr.runtime.RecognitionException;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import net.sourceforge.jFuzzyLogic.FIS;
import edu.boun.edgecloudsim.cloud_server.CloudVM;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.SimLogger;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;


public class DeepEdgeOrchestrator extends EdgeOrchestrator {

    public ArrayList <Double> m_taskFeatures;
    private boolean isTraining = false;
    public static final double MAX_DATA_SIZE=2500;

    private int numberOfHost; //used by load balancer
    private FIS fis1 = null;
    private FIS fis2 = null;
    private FIS fis3 = null;

    private MultiLayerNetwork m_agent = null;
    private double numberOfWlanOffloadedTask = 0;
    private double numberOfManOffloadedTask = 0;
    private double numberOfWanOffloadedTask = 0;
    private double activeManTaskCount = 0;
    private double activeWanTaskCount = 0;
    private double totalSizeOfActiveManTasks = 0;
    private int counter = 0;

    public DeepEdgeOrchestrator (String _policy, String _simScenario) {
        super(_policy, _simScenario);
    }

    @Override
    public void initialize() {
        numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();

        try {
            fis1 = FIS.createFromString(FCL_definition.fclDefinition1, false);
            fis2 = FIS.createFromString(FCL_definition.fclDefinition2, false);
            fis3 = FIS.createFromString(FCL_definition.fclDefinition3, false);
        } catch (RecognitionException e) {
            SimLogger.printLine("Cannot generate FIS! Terminating simulation...");
            e.printStackTrace();
            System.exit(0);
        }

        if (policy.equals("DDQN")){

            try {
                final String absolutePath = "TheModel";
                m_agent = MultiLayerNetwork.load(new File(absolutePath), false);
            }catch (IOException e) {
                e.printStackTrace();
            }


        }
    }


    /*
     * (non-Javadoc)
     * @see edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator#getDeviceToOffload(edu.boun.edgecloudsim.edge_client.Task)
     *
     * It is assumed that the edge orchestrator app is running on the edge devices in a distributed manner
     */
    @Override
    public int getDeviceToOffload(Task task) {
        int result = 0;

        //RODO: return proper host ID


        if(simScenario.equals("SINGLE_TIER")){
            result = SimSettings.GENERIC_EDGE_DEVICE_ID;
        }
        else if(simScenario.equals("TWO_TIER_WITH_EO")){

            if(policy.equals("DDQN")){
                counter++;
                if (counter > 75000){
                    //System.out.println("Counter: "+counter);
                    Task dummyTask = new Task(0, 0, 0, 0, 128, 128, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
                    double wanDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(),
                            SimSettings.CLOUD_DATACENTER_ID, dummyTask /* 1 Mbit */);
                    double wanBW = (wanDelay == 0) ? 0 : (1 / wanDelay); /* Mbps */
                    if(wanBW > 6)
                        result = SimSettings.CLOUD_DATACENTER_ID;
                    else
                        result = SimSettings.GENERIC_EDGE_DEVICE_ID;
                }else{
                    DeepEdgeState currentState = GetFeaturesForAgent(task);
                    INDArray output = m_agent.output(currentState.getState());
                    result = output.argMax().getInt();

                    if (result == 14){
                        numberOfWanOffloadedTask++;
                    }
                    else if(task.getSubmittedLocation().getServingWlanId() == result){
                        numberOfWlanOffloadedTask++;
                    }
                    else{
                        numberOfManOffloadedTask++;
                    }
                }

            }else{

                int bestRemoteEdgeHostIndex = 0;
                int nearestEdgeHostIndex = 0;
                double nearestEdgeUtilization = 0;

                //dummy task to simulate a task with 1 Mbit file size to upload and download
                Task dummyTask = new Task(0, 0, 0, 0, 128, 128, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());

                double wanDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(),
                        SimSettings.CLOUD_DATACENTER_ID, dummyTask /* 1 Mbit */);
                double wanBW = (wanDelay == 0) ? 0 : (1 / wanDelay); /* Mbps */

                double manDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID,
                        SimSettings.GENERIC_EDGE_DEVICE_ID, dummyTask /* 1 Mbit */);

                double edgeUtilization = SimManager.getInstance().getEdgeServerManager().getAvgUtilization();

                //finding least loaded neighbor edge host
                double bestRemoteEdgeUtilization = 100; //start with max value
                if (isTraining){
                    m_taskFeatures = new ArrayList<>();
                    m_taskFeatures.add(wanBW);
                    m_taskFeatures.add(wanDelay);
                    m_taskFeatures.add((double)task.getCloudletLength());
                    m_taskFeatures.add(edgeUtilization);
                    m_taskFeatures.add(manDelay);
                }
                for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
                    List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);

                    double totalUtlization=0;
                    for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
                        totalUtlization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
                    }

                    double avgUtilization = (totalUtlization / (double)(vmArray.size()));

                    if (isTraining){
                        m_taskFeatures.add(avgUtilization);
                    }

                    EdgeHost host = (EdgeHost)(vmArray.get(0).getHost()); //all VMs have the same host
                    if(host.getLocation().getServingWlanId() == task.getSubmittedLocation().getServingWlanId()){
                        nearestEdgeUtilization = totalUtlization / (double)(vmArray.size());
                        nearestEdgeHostIndex = hostIndex;
                    }
                    else if(avgUtilization < bestRemoteEdgeUtilization){
                        bestRemoteEdgeHostIndex = hostIndex;
                        bestRemoteEdgeUtilization = avgUtilization;
                    }
                }
                if (isTraining){
                    m_taskFeatures.add(nearestEdgeUtilization);
                    m_taskFeatures.add((double)nearestEdgeHostIndex);
                    m_taskFeatures.add(bestRemoteEdgeUtilization);
                    m_taskFeatures.add((double)bestRemoteEdgeHostIndex);
                }

                if(policy.equals("FUZZY_BASED")){
                    int bestHostIndex = nearestEdgeHostIndex;
                    double bestHostUtilization = nearestEdgeUtilization;

                    // Set inputs
                    fis2.setVariable("man_delay", manDelay);
                    fis2.setVariable("nearest_edge_uitl", nearestEdgeUtilization);
                    fis2.setVariable("best_remote_edge_uitl", bestRemoteEdgeUtilization);

                    // Evaluate
                    fis2.evaluate();

		        /*
		        SimLogger.printLine("########################################");
		        SimLogger.printLine("man bw: " + manBW);
		        SimLogger.printLine("nearest_edge_uitl: " + nearestEdgeUtilization);
		        SimLogger.printLine("best_remote_edge_uitl: " + bestRemoteEdgeHostUtilization);
		        SimLogger.printLine("offload_decision: " + fis2.getVariable("offload_decision").getValue());
		        SimLogger.printLine("########################################");
				*/

                    if(fis2.getVariable("offload_decision").getValue() > 50){
                        bestHostIndex = bestRemoteEdgeHostIndex;
                        bestHostUtilization = bestRemoteEdgeUtilization;
                    }

                    double delay_sensitivity = SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][12];

                    // Set inputs
                    fis1.setVariable("wan_bw", wanBW);
                    fis1.setVariable("task_size", task.getCloudletLength());
                    fis1.setVariable("delay_sensitivity", delay_sensitivity);
                    fis1.setVariable("avg_edge_util", bestHostUtilization);

                    // Evaluate
                    fis1.evaluate();

		        /*
		        SimLogger.printLine("########################################");
		        SimLogger.printLine("wan bw: " + wanBW);
		        SimLogger.printLine("task_size: " + task.getCloudletLength());
		        SimLogger.printLine("delay_sensitivity: " + delay_sensitivity);
		        SimLogger.printLine("avg_edge_util: " + bestHostUtilization);
		        SimLogger.printLine("offload_decision: " + fis1.getVariable("offload_decision").getValue());
		        SimLogger.printLine("########################################");
		        */

                    if(fis1.getVariable("offload_decision").getValue() > 50){
                        result = SimSettings.CLOUD_DATACENTER_ID;
                    }
                    else{
                        result = bestHostIndex;
                    }
                }
                else if(policy.equals("FUZZY_COMPETITOR")){
                    double utilization = edgeUtilization;
                    double cpuSpeed = (double)100 - utilization;
                    double videoExecution = SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][12];
                    double dataSize = task.getCloudletFileSize() + task.getCloudletOutputSize();
                    double normalizedDataSize = Math.min(MAX_DATA_SIZE, dataSize)/MAX_DATA_SIZE;

                    // Set inputs
                    fis3.setVariable("wan_bw", wanBW);
                    fis3.setVariable("cpu_speed", cpuSpeed);
                    fis3.setVariable("video_execution", videoExecution);
                    fis3.setVariable("data_size", normalizedDataSize);

                    // Evaluate
                    fis3.evaluate();

		        /*
		        SimLogger.printLine("########################################");
		        SimLogger.printLine("wan bw: " + wanBW);
		        SimLogger.printLine("cpu_speed: " + cpuSpeed);
		        SimLogger.printLine("video_execution: " + videoExecution);
		        SimLogger.printLine("data_size: " + normalizedDataSize);
		        SimLogger.printLine("offload_decision: " + fis2.getVariable("offload_decision").getValue());
		        SimLogger.printLine("########################################");
				*/

                    if(fis3.getVariable("offload_decision").getValue() > 50)
                        result = SimSettings.CLOUD_DATACENTER_ID;
                    else
                        result = SimSettings.GENERIC_EDGE_DEVICE_ID;
                }


                else if(policy.equals("NETWORK_BASED")){
                    if(wanBW > 6)
                        result = SimSettings.CLOUD_DATACENTER_ID;
                    else
                        result = SimSettings.GENERIC_EDGE_DEVICE_ID;
                }
                else if(policy.equals("UTILIZATION_BASED")){
                    double utilization = edgeUtilization;
                    if(utilization > 80)
                        result = SimSettings.CLOUD_DATACENTER_ID;
                    else
                        result = SimSettings.GENERIC_EDGE_DEVICE_ID;
                }
                else if(policy.equals("HYBRID")){
                    double utilization = edgeUtilization;
                    if(wanBW > 6 && utilization > 80)
                        result = SimSettings.CLOUD_DATACENTER_ID;
                    else
                        result = SimSettings.GENERIC_EDGE_DEVICE_ID;
                }
                else {
                    SimLogger.printLine("Unknow edge orchestrator policy! Terminating simulation...");
                    System.exit(0);
                }
            }

            }
        else {
            SimLogger.printLine("Unknow simulation scenario! Terminating simulation...");
            System.exit(0);
        }

        return result;
    }

    public DeepEdgeState GetFeaturesForAgent(Task task){
        Task dummyTask = new Task(0, 0, 0, 0, 128, 128, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());

        DeepEdgeState currentState = new DeepEdgeState();
        ArrayList<Double> edgeCapacities = new ArrayList<>();

        int numberOfHost = SimSettings.getInstance().getNumOfEdgeHosts();

        double wanDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(),
                SimSettings.CLOUD_DATACENTER_ID, dummyTask /* 1 Mbit */);

        double wanBW = (wanDelay == 0) ? 0 : (1 / wanDelay); /* Mbps */

        currentState.setWanBw(wanBW/20.21873);

        double manDelayF = SimManager.getInstance().getNetworkModel().getUploadDelayForTraining(SimSettings.GENERIC_EDGE_DEVICE_ID,
                SimSettings.GENERIC_EDGE_DEVICE_ID, dummyTask );

        double manBW = (manDelayF == 0) ? 0 : (1 / manDelayF);


        double manDelay = getManDelayForAgent();
        currentState.setManDelay(manDelay);

        double taskRequiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(SimSettings.VM_TYPES.EDGE_VM);
        currentState.setTaskReqCapacity(taskRequiredCapacity/800);

        int wlanID = task.getSubmittedLocation().getServingWlanId();
        currentState.setWlanID((double)wlanID / (numberOfHost - 1));

        int nearestEdgeHostId = 0;


        for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
            List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
            EdgeHost host = (EdgeHost)(vmArray.get(0).getHost()); //all VMs have the same host

            double totalUtilizationForEdgeServer=0;
            for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
                totalUtilizationForEdgeServer += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
            }

            double totalCapacity = 100 * vmArray.size();
            double averageCapacity = (totalCapacity - totalUtilizationForEdgeServer)  / vmArray.size();
            double normalizedCapacity = averageCapacity / 100;

            if (normalizedCapacity < 0){
                normalizedCapacity = 0;
            }
            edgeCapacities.add(normalizedCapacity);

            if (host.getLocation().getServingWlanId() == task.getSubmittedLocation().getServingWlanId()){
                nearestEdgeHostId = hostIndex;
            }

        }

        currentState.setAvailVmInEdge(edgeCapacities);
        currentState.setNearestEdgeHostId((double)nearestEdgeHostId / numberOfHost);

        double delay_sensitivity = SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][12];

        currentState.setDelaySensitivity(delay_sensitivity);

        currentState.setNumberOfWlanOffloadedTask(numberOfWlanOffloadedTask/75000);
        currentState.setNumberOfManOffloadedTask(numberOfManOffloadedTask/75000);
        currentState.setNumberOfWanOffloadedTask(numberOfWanOffloadedTask/75000);
        currentState.setActiveManTaskCount(activeManTaskCount/25);
        currentState.setActiveWanTaskCount(activeWanTaskCount/25);


        return currentState;

    }

    public double getManDelayForAgent(){
        double delay = 0;
        double mu = 0;
        double lambda = 0;
        double bandwidth = 1300*1024; //Kbps , C

        if (totalSizeOfActiveManTasks == 0){
            mu = bandwidth;
        }else{
            mu = bandwidth / (totalSizeOfActiveManTasks * 8);
        }

        lambda = activeManTaskCount;


        if (lambda >= mu){
            return 0;
        }else{
            delay = 1 / (mu - lambda);
            return delay;
        }
    }


    @Override
    public Vm getVmToOffload(Task task, int deviceId) {
        Vm selectedVM = null;

        if(deviceId == SimSettings.CLOUD_DATACENTER_ID){
            //Select VM on cloud devices via Least Loaded algorithm!
            double selectedVmCapacity = 0; //start with min value
            List<Host> list = SimManager.getInstance().getCloudServerManager().getDatacenter().getHostList();
            for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
                List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
                for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
                    double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
                    double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
                    if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
                        selectedVM = vmArray.get(vmIndex);
                        selectedVmCapacity = targetVmCapacity;
                    }
                }
            }
        }
        else if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
            //Select VM on edge devices via Least Loaded algorithm!
            double selectedVmCapacity = 0; //start with min value
            for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
                List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
                for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
                    double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
                    double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
                    if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
                        selectedVM = vmArray.get(vmIndex);
                        selectedVmCapacity = targetVmCapacity;
                    }
                }
            }
        }
        else{
            //if the host is specifically defined!
            List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(deviceId);

            //Select VM on edge devices via Least Loaded algorithm!
            double selectedVmCapacity = 0; //start with min value
            for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
                double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
                double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
                if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
                    selectedVM = vmArray.get(vmIndex);
                    selectedVmCapacity = targetVmCapacity;
                }
            }
        }

        /*
        if (selectedVM == null){
            List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(deviceId);

            //Select VM on edge devices via Least Loaded algorithm!
            double selectedVmCapacity = 0; //start with min value
            for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
                double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
                double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
                if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
                    selectedVM = vmArray.get(vmIndex);
                    selectedVmCapacity = targetVmCapacity;
                }
            }
        }

         */


        return selectedVM;
    }

    @Override
    public void processEvent(SimEvent arg0) {
        // Nothing to do!
    }

    @Override
    public void shutdownEntity() {
        // Nothing to do!
    }

    @Override
    public void startEntity() {
        // Nothing to do!
    }

}
