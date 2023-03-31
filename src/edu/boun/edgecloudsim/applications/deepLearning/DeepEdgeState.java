package edu.boun.edgecloudsim.applications.deepLearning;

import org.cloudbus.cloudsim.core.CloudSim;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;

public class DeepEdgeState {


    private double wanBw;
    private double wanDelay;
    private double manBw;
    private double manDelay; // normally this was not implemented in original DeepEdge
    private double taskReqCapacity;
    private double wlanID; // of mobile device
    private double availVMInWlanEdge; // implemented in original DeepEdge
    private ArrayList<Double> availVmInEdge; //new (14)
    private double nearestEdgeHostId;
    private double delaySensitivity;
    private double mostAvailEdgeID; // implemented in original solution. It requires preprocessing.

    private double dataUplad;
    private double dataDownload;
    private double taskLength;
    private int featureCount;
    private int stateId;


    private double wlanDelay;


    private double activeManTaskCount;
    private double activeWanTaskCount;
    private double numberOfWlanOffloadedTask;
    private double numberOfManOffloadedTask;
    private double numberOfWanOffloadedTask;
    private double timestampOfState;

    private static int counterForId = 1; //initialized from 1 to provide correspondance with task ids
    private final double EPISODE_SIZE = 75000;

    public DeepEdgeState(){
        this.timestampOfState = CloudSim.clock() / 300;
        this.stateId = counterForId;
        counterForId++;
        if (counterForId > EPISODE_SIZE){
            counterForId = 1;
        }


        this.featureCount = 9 + 14;
    }

    public INDArray getState(){
        INDArray stateInfo = Nd4j.zeros(1, this.featureCount);

        stateInfo.putScalar(0, getWanBw());
        stateInfo.putScalar(1, getManDelay());
        stateInfo.putScalar(2, getTaskReqCapacity());
        stateInfo.putScalar(3, getWlanID());
        stateInfo.putScalar(4, getDelaySensitivity());
        stateInfo.putScalar(5, getActiveManTaskCount());
        stateInfo.putScalar(6, getNumberOfWlanOffloadedTask());
        stateInfo.putScalar(7, getNumberOfManOffloadedTask());
        stateInfo.putScalar(8, getNumberOfWanOffloadedTask());

        for(int i = 9; i < this.featureCount; i++ ){
            stateInfo.putScalar(i, getAvailVmInEdge().get(i-9));
        }

        return stateInfo;
    }


    public void setManBw(double manBw) {
        this.manBw = manBw;
    }

    public int getStateId(){
        return this.stateId;
    }

    public double getWanBw() {
        return wanBw;
    }

    public double getWanDelay() {
        return wanDelay;
    }

    public double getManBw() {
        return manBw;
    }

    public double getManDelay() {
        return manDelay;
    }

    public double getTaskReqCapacity() {
        return taskReqCapacity;
    }

    public double getWlanID() {
        return wlanID;
    }

    public double getAvailVMInWlanEdge() {
        return availVMInWlanEdge;
    }

    public ArrayList<Double> getAvailVmInEdge() {
        return availVmInEdge;
    }

    public double getNearestEdgeHostId() {
        return nearestEdgeHostId;
    }

    public double getDelaySensitivity() {
        return delaySensitivity;
    }

    public double getMostAvailEdgeID() {
        return mostAvailEdgeID;
    }

    public double getDataUplad() {
        return dataUplad;
    }

    public double getDataDownload() {
        return dataDownload;
    }

    public double getTaskLength() {
        return taskLength;
    }

    public double getTimestampOfState() {
        return timestampOfState;
    }

    public double getWlanDelay() {
        return wlanDelay;
    }

    public void setWlanDelay(double wlanDelay) {
        this.wlanDelay = wlanDelay;
    }

    public void setWanDelay(double wanDelay) {
        this.wanDelay = wanDelay;
    }

    public void setManDelay(double manDelay) {
        this.manDelay = manDelay;
    }

    public double getActiveManTaskCount() {
        return activeManTaskCount;
    }

    public void setActiveManTaskCount(double activeManTaskCount) {
        this.activeManTaskCount = activeManTaskCount;
    }

    public double getActiveWanTaskCount() {
        return activeWanTaskCount;
    }

    public void setActiveWanTaskCount(double activeWanTaskCount) {
        this.activeWanTaskCount = activeWanTaskCount;
    }

    public double getNumberOfWlanOffloadedTask() {
        return numberOfWlanOffloadedTask;
    }

    public void setNumberOfWlanOffloadedTask(double numberOfWlanOffloadedTask) {
        this.numberOfWlanOffloadedTask = numberOfWlanOffloadedTask;
    }

    public double getNumberOfManOffloadedTask() {
        return numberOfManOffloadedTask;
    }

    public void setNumberOfManOffloadedTask(double numberOfManOffloadedTask) {
        this.numberOfManOffloadedTask = numberOfManOffloadedTask;
    }

    public double getNumberOfWanOffloadedTask() {
        return numberOfWanOffloadedTask;
    }

    public void setNumberOfWanOffloadedTask(double numberOfWanOffloadedTask) {
        this.numberOfWanOffloadedTask = numberOfWanOffloadedTask;
    }

    public void setWanBw(double wanBw) {
        this.wanBw = wanBw;
    }

    public void setTaskReqCapacity(double taskReqCapacity) {
        this.taskReqCapacity = taskReqCapacity;
    }

    public void setWlanID(double wlanID) {
        this.wlanID = wlanID;
    }

    public void setAvailVmInEdge(ArrayList<Double> availVmInEdge) {
        this.availVmInEdge = availVmInEdge;
    }

    public void setNearestEdgeHostId(double nearestEdgeHostId) {
        this.nearestEdgeHostId = nearestEdgeHostId;
    }

    public void setDelaySensitivity(double delaySensitivity) {
        this.delaySensitivity = delaySensitivity;
    }

}
