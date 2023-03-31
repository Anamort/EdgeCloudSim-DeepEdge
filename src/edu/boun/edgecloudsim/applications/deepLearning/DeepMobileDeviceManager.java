package edu.boun.edgecloudsim.applications.deepLearning;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;



import java.util.*;
import java.io.*;

public class DeepMobileDeviceManager extends MobileDeviceManager{
    private static final int BASE = 100000; //start from base in order not to conflict cloudsim tag!

    private static final int UPDATE_MM1_QUEUE_MODEL = BASE + 1;
    private static final int REQUEST_RECEIVED_BY_CLOUD = BASE + 2;
    private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 3;
    private static final int REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE = BASE + 4;
    private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR = BASE + 5;
    private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 6;
    private static final int RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE = BASE + 7;

    private static final double MM1_QUEUE_MODEL_UPDATE_INTEVAL = 5; //seconds


    private int taskIdCounter=0;

    private boolean dqnTraining = false;

    private final int EPISODE_SIZE = 75000;

    private double numberOfWlanOffloadedTask = 0;
    private double numberOfManOffloadedTask = 0;
    private double numberOfWanOffloadedTask = 0;
    private double activeManTaskCount = 0;
    private double activeWanTaskCount = 0;
    private double totalSizeOfActiveManTasks = 0;
    private double totalReward = 0;

    private HashMap<Integer, HashMap<Integer, Integer>> taskToStateActionPair = new HashMap<>();
    private HashMap<Integer, MemoryItem> stateIDToMemoryItemPair = new HashMap<>();

    private static DeepMobileDeviceManager instance = null;

    public  DeepMobileDeviceManager () throws Exception{

    }

    @Override
    public void initialize() {
        instance = this;
    }

    @Override
    public UtilizationModel getCpuUtilizationModel() {
        return new CpuUtilizationModel_Custom();
    }

    @Override
    public void startEntity() {
        super.startEntity();
        schedule(getId(), SimSettings.CLIENT_ACTIVITY_START_TIME +
                MM1_QUEUE_MODEL_UPDATE_INTEVAL, UPDATE_MM1_QUEUE_MODEL);
    }

    public static DeepMobileDeviceManager getInstance(){
        return instance;
    }

    /**
     * Submit cloudlets to the created VMs.
     *
     * @pre $none
     * @post $none
     */
    protected void submitCloudlets() {
        //do nothing!
    }

    /**
     * Process a cloudlet return event.
     *
     * @param ev a SimEvent object
     * @pre ev != $null
     * @post $none
     */
    protected void processCloudletReturn(SimEvent ev) {
        NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
        Task task = (Task) ev.getData();

        SimLogger.getInstance().taskExecuted(task.getCloudletId());



        if(task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID){
            //SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": task #" + task.getCloudletId() + " received from cloud");
            double WanDelay = networkModel.getDownloadDelay(SimSettings.CLOUD_DATACENTER_ID, task.getMobileDeviceId(), task);
            if(WanDelay > 0)
            {
                Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+WanDelay);
                if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
                {
                    networkModel.downloadStarted(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
                    SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), WanDelay, NETWORK_DELAY_TYPES.WAN_DELAY);
                    schedule(getId(), WanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
                }
                else
                {
                    SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
                    if (dqnTraining){
                        TrainAgent(task, true);
                    }
                }
            }
            else
            {
                SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WAN_DELAY);
                if (dqnTraining){
                    TrainAgent(task, true);
                }
            }
        }
        else{
            int nextEvent = RESPONSE_RECEIVED_BY_MOBILE_DEVICE;
            int nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
            NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
            double delay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task);

            EdgeHost host = (EdgeHost)(SimManager.
                    getInstance().
                    getEdgeServerManager().
                    getDatacenterList().get(task.getAssociatedHostId()).
                    getHostList().get(0));

            //if neighbor edge device is selected
            if(host.getLocation().getServingWlanId() != task.getSubmittedLocation().getServingWlanId())
            {
                delay = networkModel.getDownloadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID, SimSettings.GENERIC_EDGE_DEVICE_ID, task);
                nextEvent = RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE;
                nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID + 1;
                delayType = NETWORK_DELAY_TYPES.MAN_DELAY;
            }

            if(delay > 0)
            {
                Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+delay);
                if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
                {
                    networkModel.downloadStarted(currentLocation, nextDeviceForNetworkModel);
                    SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay, delayType);

                    schedule(getId(), delay, nextEvent, task);
                }
                else
                {
                    SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
                    if (dqnTraining){
                        TrainAgent(task, true);
                    }
                }
            }
            else
            {
                SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
                if (dqnTraining){
                    TrainAgent(task, true);
                }
            }
        }
    }

    protected void processOtherEvent(SimEvent ev) {
        if (ev == null) {
            SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
            System.exit(0);
            return;
        }

        NetworkModel networkModel = SimManager.getInstance().getNetworkModel();

        switch (ev.getTag()) {
            case UPDATE_MM1_QUEUE_MODEL:
            {
                ((NetworkingModel)networkModel).updateMM1QueeuModel();
                schedule(getId(), MM1_QUEUE_MODEL_UPDATE_INTEVAL, UPDATE_MM1_QUEUE_MODEL);

                break;
            }
            case REQUEST_RECEIVED_BY_CLOUD:
            {
                Task task = (Task) ev.getData();
                networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
                if (dqnTraining){
                    activeWanTaskCount--;
                }
                submitTaskToVm(task, SimSettings.VM_TYPES.CLOUD_VM);
                break;
            }
            case REQUEST_RECEIVED_BY_EDGE_DEVICE:
            {
                Task task = (Task) ev.getData();
                networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
                submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
                break;
            }
            case REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE:
            {
                Task task = (Task) ev.getData();
                networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID+1);
                if (dqnTraining){
                    activeManTaskCount--;
                    totalSizeOfActiveManTasks -= SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][5];
                }
                submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);

                break;
            }
            case REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR:
            {
                Task task = (Task) ev.getData();
                networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);

                double manDelay =  networkModel.getUploadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID, SimSettings.GENERIC_EDGE_DEVICE_ID, task);
                if(manDelay>0){
                    networkModel.uploadStarted(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID+1);
                    if (dqnTraining){
                        activeManTaskCount++;
                        totalSizeOfActiveManTasks += SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][5];
                    }
                    SimLogger.getInstance().setUploadDelay(task.getCloudletId(), manDelay, NETWORK_DELAY_TYPES.MAN_DELAY);
                    schedule(getId(), manDelay, REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE, task);
                }
                else
                {
                    //SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
                    SimLogger.getInstance().rejectedDueToBandwidth(
                            task.getCloudletId(),
                            CloudSim.clock(),
                            SimSettings.VM_TYPES.EDGE_VM.ordinal(),
                            NETWORK_DELAY_TYPES.MAN_DELAY);
                    if (dqnTraining){
                        TrainAgent(task, true);
                    }
                }

                break;
            }
            case RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE:
            {
                Task task = (Task) ev.getData();
                networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID+1);

                //SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": task #" + task.getCloudletId() + " received from edge");
                double delay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task);

                if(delay > 0)
                {
                    Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+delay);
                    if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
                    {
                        networkModel.downloadStarted(currentLocation, SimSettings.GENERIC_EDGE_DEVICE_ID);
                        SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay, NETWORK_DELAY_TYPES.WLAN_DELAY);
                        schedule(getId(), delay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
                    }
                    else
                    {
                        SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
                        if (dqnTraining){
                            TrainAgent(task, true);
                        }
                    }
                }
                else
                {
                    SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WLAN_DELAY);
                    if (dqnTraining){
                        TrainAgent(task, true);
                    }
                }

                break;
            }
            case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
            {
                Task task = (Task) ev.getData();

                if(task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID)
                    networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
                else if(task.getAssociatedDatacenterId() != SimSettings.MOBILE_DATACENTER_ID)
                    networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);



                SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
                if (dqnTraining){
                    TrainAgent(task, false);
                }
                break;
            }
            default:
                SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
                System.exit(0);
                break;
        }
    }

    public void submitTask(TaskProperty edgeTask) {

        //for this given task, for TRAINING, each edge server must be evaluated

        int vmType=0;
        int nextEvent=0;
        int nextDeviceForNetworkModel;
        NETWORK_DELAY_TYPES delayType;
        double delay=0;

        NetworkModel networkModel = SimManager.getInstance().getNetworkModel();

        //create a task
        Task task = createTask(edgeTask);

        Location currentLocation = SimManager.getInstance().getMobilityModel().
                getLocation(task.getMobileDeviceId(), CloudSim.clock());

        //set location of the mobile device which generates this task
        task.setSubmittedLocation(currentLocation);

        //add related task to log list
        SimLogger.getInstance().addLog(task.getCloudletId(),
                task.getTaskType(),
                (int)task.getCloudletLength(),
                (int)task.getCloudletFileSize(),
                (int)task.getCloudletOutputSize());


        int nextHopId;

        if (dqnTraining){

            DDQNAgent agent = DDQNAgent.getInstance();
            DeepEdgeState currentState = GetFeaturesForAgent(task);

            nextHopId = agent.DoAction(currentState);

            if (nextHopId == 14){
                numberOfWanOffloadedTask++;
            }
            else if(task.getSubmittedLocation().getServingWlanId() == nextHopId){
                numberOfWlanOffloadedTask++;
            }
            else{
                numberOfManOffloadedTask++;
            }


            HashMap<Integer, Integer> stateActionP = new HashMap<>();
            stateActionP.put(currentState.getStateId(), nextHopId);
            taskToStateActionPair.put(task.getCloudletId(), stateActionP);
            boolean isDone = false;
            if (task.getCloudletId() == EPISODE_SIZE){
                isDone = true;
                dqnTraining = false;
                System.out.println("Total Reward devicemanager: " + totalReward);
            }


            // After work
            MemoryItem memoryItem;
            ArrayList<Double> edgeList = currentState.getAvailVmInEdge();
            if (nextHopId < 14 && edgeList.get(nextHopId) == 0){
                memoryItem = new MemoryItem(currentState, null, -1, -10, isDone);
            }else{
                memoryItem = new MemoryItem(currentState, null, -10, -10, isDone);
            }
            // After work



            stateIDToMemoryItemPair.put(currentState.getStateId(), memoryItem);

            if (stateIDToMemoryItemPair.get(currentState.getStateId()-1) != null){
                MemoryItem previousMemoryItem = stateIDToMemoryItemPair.get(currentState.getStateId()-1);
                previousMemoryItem.setNextState(currentState); //pass by reference!! It is crucial!!
            }


        }
        else{
            nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task);
        }


        if (nextHopId == SimManager.getInstance().getEdgeServerManager().getDatacenterList().size()){
            nextHopId = SimSettings.CLOUD_DATACENTER_ID;
        }

        if(nextHopId == SimSettings.CLOUD_DATACENTER_ID){
            delay = networkModel.getUploadDelay(task.getMobileDeviceId(), SimSettings.CLOUD_DATACENTER_ID, task);
            vmType = SimSettings.VM_TYPES.CLOUD_VM.ordinal();
            nextEvent = REQUEST_RECEIVED_BY_CLOUD;
            delayType = NETWORK_DELAY_TYPES.WAN_DELAY;
            nextDeviceForNetworkModel = SimSettings.CLOUD_DATACENTER_ID;
        }
        else {
            delay = networkModel.getUploadDelay(task.getMobileDeviceId(), SimSettings.GENERIC_EDGE_DEVICE_ID, task);
            vmType = SimSettings.VM_TYPES.EDGE_VM.ordinal();
            nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE;
            delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
            nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
        }

        if(delay>0){

            Vm selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task, nextHopId);

            if(selectedVM != null){
                //set related host id
                task.setAssociatedDatacenterId(nextHopId);

                //set related host id
                task.setAssociatedHostId(selectedVM.getHost().getId());

                //set related vm id
                task.setAssociatedVmId(selectedVM.getId());

                //bind task to related VM
                getCloudletList().add(task);
                bindCloudletToVm(task.getCloudletId(), selectedVM.getId());

                if(selectedVM instanceof EdgeVM){
                    EdgeHost host = (EdgeHost)(selectedVM.getHost());

                    //if neighbor edge device is selected
                    if(host.getLocation().getServingWlanId() != task.getSubmittedLocation().getServingWlanId()){
                        nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR;
                    }
                }
                networkModel.uploadStarted(currentLocation, nextDeviceForNetworkModel);
                if (dqnTraining && nextEvent == REQUEST_RECEIVED_BY_CLOUD){
                    activeWanTaskCount++;
                }

                SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());
                SimLogger.getInstance().setUploadDelay(task.getCloudletId(), delay, delayType);

                schedule(getId(), delay, nextEvent, task);
            }
            else{
                //SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
                SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), vmType);
                if (dqnTraining){
                    TrainAgent(task, true);
                }

            }
        }
        else
        {
            //SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
            SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), vmType, delayType);
            if (dqnTraining){
                TrainAgent(task, true);
            }
        }
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


    private void TrainAgent(Task task, boolean isFailed){

        DDQNAgent agent = DDQNAgent.getInstance();

        double reward;
        if (isFailed){
            reward = -1;
        }
        else{
            reward = 1;
        }
        totalReward += reward;
        agent.setReward(totalReward);


        HashMap<Integer, Integer> pair = taskToStateActionPair.get(task.getCloudletId());

        //System.out.println("Size of taskToStateActionPair: "+ taskToStateActionPair.size());
        int stateId = pair.entrySet().iterator().next().getKey();
        int selectedAction = pair.entrySet().iterator().next().getValue();

        if (stateIDToMemoryItemPair.get(stateId) != null){
            MemoryItem memoryItem = stateIDToMemoryItemPair.get(stateId); // pass by reference!! vital!!
            memoryItem.setAction(selectedAction);

            if (memoryItem.getValue() == -10){
                memoryItem.setValue(reward);
            }

        }else{
            System.out.println("ERROR!");
        }



        ArrayList<Integer> toBeDeletedIds = new ArrayList<>();
        for (Map.Entry<Integer, MemoryItem> stateIDToMemoryItem: stateIDToMemoryItemPair.entrySet() ){
            int id = stateIDToMemoryItem.getKey();
            MemoryItem item = stateIDToMemoryItem.getValue();

            if (item.getNextState() != null && item.getState() != null && item.getAction() != -10 && item.getValue() != -10){
                //it means that the agent can be trained with this item
                agent.DDQN(item.getState(), item.getNextState(), item.getValue(), item.getAction(), item.isDone());
                toBeDeletedIds.add(id);
            }
        }
        for (Integer id: toBeDeletedIds){
            stateIDToMemoryItemPair.remove(id);
        }
        taskToStateActionPair.remove(task.getCloudletId()); //remove task to state-action pair

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
            int numberOfAvailableVms = 0;
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

        currentState.setNumberOfWlanOffloadedTask(numberOfWlanOffloadedTask/EPISODE_SIZE);
        currentState.setNumberOfManOffloadedTask(numberOfManOffloadedTask/EPISODE_SIZE);
        currentState.setNumberOfWanOffloadedTask(numberOfWanOffloadedTask/EPISODE_SIZE);
        currentState.setActiveManTaskCount(activeManTaskCount/25);
        currentState.setActiveWanTaskCount(activeWanTaskCount/25);


        return currentState;

    }



    private void submitTaskToVm(Task task, SimSettings.VM_TYPES vmType) {
        //SimLogger.printLine(CloudSim.clock() + ": Cloudlet#" + task.getCloudletId() + " is submitted to VM#" + task.getVmId());

        schedule(getVmsToDatacentersMap().get(task.getVmId()), 0, CloudSimTags.CLOUDLET_SUBMIT, task);

        SimLogger.getInstance().taskAssigned(task.getCloudletId(),
                task.getAssociatedDatacenterId(),
                task.getAssociatedHostId(),
                task.getAssociatedVmId(),
                vmType.ordinal());

    }

    private Task createTask(TaskProperty edgeTask){
        UtilizationModel utilizationModel = new UtilizationModelFull(); /*UtilizationModelStochastic*/
        UtilizationModel utilizationModelCPU = getCpuUtilizationModel();

        Task task = new Task(edgeTask.getMobileDeviceId(), ++taskIdCounter,
                edgeTask.getLength(), edgeTask.getPesNumber(),
                edgeTask.getInputFileSize(), edgeTask.getOutputFileSize(),
                utilizationModelCPU, utilizationModel, utilizationModel);

        //set the owner of this task
        task.setUserId(this.getId());
        task.setTaskType(edgeTask.getTaskType());

        if (utilizationModelCPU instanceof CpuUtilizationModel_Custom) {
            ((CpuUtilizationModel_Custom)utilizationModelCPU).setTask(task);
        }

        return task;
    }
}
