# EdgeCloudSim - DeepEdge 

EdgeCloudSim provides a simulation environment specific to Edge Computing scenarios where it is possible to conduct experiments that consider both computational and networking resources. In this particular implementation, we enhance the features of EdgeCloudSim to apply DRL (Deep Reinforcement Learning) solutions in an edge computing environment. We use the Double DQN method in this implementation by utilizing the Deeplearning4j library. If you want to use this implementation in your research work, please cite our paper [[1]](https://ieeexplore.ieee.org/document/9930655).

## Discussion Forum

The discussion forum for EdgeCloudSim can be found [here](https://groups.google.com/forum/#!forum/edgecloudsim).
We hope to meet with all interested parties in this forum.
Please feel free to join and let us discuss issues, share ideas related to EdgeCloudSim all togerther.

## New Classes

### TrainingEdge.java

This is the class where the simulation starts. For the training mode, the *isTrainingForDDQN* flag should be *true*. On the other hand, to test the performance of the trained model, that flag should be set as *false*. The number of episodes to train the model can be changed through the *numberOfEpisodes* variable. The default value is 100.


### DDQNAgent.java
This is the class where the DDQN agent is defined, and the corresponding neural network is created. The number of actions related to the output layer of the neural network is set to the summation of the number of edge servers and the cloud server. The model can be saved after the completion of each training episode using the *saveModel* method.


### DeepMobileDeviceManager.java

This class is responsible for managing the corresponding events in the simulator as in the original EdgeCloudSim. However, it is crucial for the DRL training as the reward mechanism is run considering the related events. Moreover, the creation of the state-action pairs, memory items, and updating the neural network are managed via this class.

### MemoryItem.java

We hold the five-tuple information, which is essential for DRL training, using this class.

### DeepEdgeState.java

The snapshot of the edge computing environment, which is a state, is stored using this class. Since DRL is based on Markov Decision Process, presenting how the corresponding state changes regarding the taken action is crucial. This state information is also an essential element for the *MemoryItem* instances.

### DeepEdgeOrchestrator.java

The orchestrator class for offloaded tasks. It is fundamentally the same as the original EdgeCloudSim module. The difference is that it uses the trained model to decide where to offload if the policy equals “DDQN”.


## Training The Agent

To train a new DRL agent, the *isTrainingForDDQN* flag in **TrainingEdge.java**, and the *dqnTraining* flag in **DeepMobileDeviceManager.java** should be equal to *true*. Note that we set the *EPISODE_SIZE* to 75000 (number of tasks) as originally we trained the DeepEdge agent for 2400 active users in the network. Moreover, the default value for the number of episodes is 100. These variables can be changed based on your project.


## Testing The Agent

To test the trained agent, the specified saved model is used by the module. The path for that model is given to the orchestrator in **DeepEdgeOrchestrator.java** using the *absolutePath*. After that, the orchestrator takes offloading decisions based on your model. Moreover, the policy should be set to “DDQN” to test the agent. It can be set inside the *orchestrator_policies* in the **default_config.properties** file along with other methods such as “NETWORK_BASED”, and “FUZZY_BASED”.




## Ease of Use
Since this is only an application of a DRL, the compilation and running mechanism is the same as EdgeCloudSim.

At the beginning of our study, we observed that too many parameters are used in the simulations and managing these parameters programmatically is difficult.
As a solution, we propose to use configuration files to manage the parameters.
EdgeCloudSim reads parameters dynamically from the following files:
- **config.properties:** Simulation settings are managed in configuration file
- **applications.xml:** Application properties are stored in xml file
- **edge_devices.xml:** Edge devices (datacenters, hosts, VMs etc.) are defined in xml file



## Compilation and Running
Since this is only an application of a DRL, the compilation and running mechanism is the same as EdgeCloudSim.

To compile sample application, *compile.sh* script which is located in *scripts/sample_application* folder can be used. You can rewrite similar script for your own application by modifying the arguments of javac command in way to declare the java file which includes your main method. Please note that this script can run on Linux based systems, including Mac OS. You can also use your favorite IDE (eclipse, netbeans etc.) to compile your project.

In order to run multiple sample_application scenarios in parallel, you can use *run_scenarios.sh* script which is located in *scripts/sample_application* folder. To run your own application, modify the java command in *runner.sh* script in a way to declare the java class which includes your main method. The details of using this script is explained in [this](/wiki/How-to-run-EdgeCloudSim-application-in-parallel) wiki page.

You can also monitor each process via the output files located under *scripts/sample_application/output/date* folder. For example:
```
./run_scenarios.sh 8 10
tail -f output/date/ite_1.log
```

## Analyzing the Results

Since this is only an application of a DRL, the compilation and running mechanism is the same as EdgeCloudSim.

At the end of each iteration, simulation results will be compressed in the *output/date/ite_n.tgz* files. When you extract these tgz files, you will see lots of log file in csv format. You can find matlab files which can plot graphics by using these files under *scripts/sample_application/matlab* folder. You can also write other scripts (e.g. python scripts) with the same manner of matlab plotter files.

## Example Output for DeepEdge Module

![Alt text](/doc/images/DeepEdge-SampleResults/Episodes.png?raw=true) 

![Alt text](/doc/images/DeepEdge-SampleResults/GeneralResults.png?raw=true)


## Publications
**[1]** B. Yamansavascilar, A. C. Baktir, Cagatay Sonmez, A. Ozgovde, and C. Ersoy, "[DeepEdge: A Deep Reinforcement Learning based Task Orchestrator for Edge Computing](https://ieeexplore.ieee.org/document/9930655)," *IEEE Transactions on Network Science and Engineering*, vol. 10, pp. 538-552, 2023.

