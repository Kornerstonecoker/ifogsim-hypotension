package org.fog.test.perfeval;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.*;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.*;
import org.fog.utils.distribution.DeterministicDistribution;

import java.util.*;

public class HypotensionDetection {

    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();
    static final int NUM_SENSORS = 8;
    static final double SENSOR_TRANSMISSION_TIME = 5.0;
    
    /** Flag to determine if simulation is cloud-only or edge/fog. */
    private static boolean CLOUD = false; // Set to true for cloud-only, false for edge/fog

    public static void main(String[] args) {
        Log.printLine("Starting Hypotension Detection Simulation...");
        
        // Parse command line arguments for deployment mode
        if (args.length >= 1) {
            CLOUD = Boolean.parseBoolean(args[0]);
        }
        
        System.out.println("Running in " + (CLOUD ? "CLOUD-ONLY" : "EDGE-WARD") + " mode");
        
        try {
            Log.disable();
            CloudSim.init(1, Calendar.getInstance(), false);
            Config.MAX_SIMULATION_TIME = 10000;

            String appId = "HypotensionApp";
            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            createFogDevices(broker.getId(), appId, application);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            
            if (CLOUD) {
                // Cloud-only deployment
                moduleMapping.addModuleToDevice("clientModule", "cloud");
                moduleMapping.addModuleToDevice("hypotensionDetector", "cloud");
            } else {
                // Edge-ward deployment
                moduleMapping.addModuleToDevice("clientModule", "edge-gateway-1");
                moduleMapping.addModuleToDevice("hypotensionDetector", "edge-gateway-1");
            }

            Controller controller = new Controller("controller", fogDevices, sensors, actuators);
            controller.submitApplication(application, 0,
                (CLOUD) ? (new ModulePlacementMapping(fogDevices, application, moduleMapping)) 
                : (new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("Simulation completed.");
            printResults();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createFogDevices(int userId, String appId, Application application) {
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 1664, 1332);
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        FogDevice gateway1 = createFogDevice("edge-gateway-1", 2800, 4000, 10000, 10000, 1, 0.0, 107.33, 83.43);
        gateway1.setParentId(cloud.getId());
        gateway1.setUplinkLatency(50);
        fogDevices.add(gateway1);

        FogDevice gateway2 = createFogDevice("edge-gateway-2", 2800, 4000, 10000, 10000, 1, 0.0, 107.33, 83.43);
        gateway2.setParentId(cloud.getId());
        gateway2.setUplinkLatency(50);
        fogDevices.add(gateway2);

        for (int i = 0; i < NUM_SENSORS; i++) {
            FogDevice sensorNode = createFogDevice("bp-sensor-" + i, 1000, 512, 1000, 1000, 2, 0.0, 87.53, 82.44);
            FogDevice parentGateway = (i < NUM_SENSORS / 2) ? gateway1 : gateway2;
            sensorNode.setParentId(parentGateway.getId());
            sensorNode.setUplinkLatency(2);
            fogDevices.add(sensorNode);

            // Use the addSensorAndActuator method for proper linking
            addSensorAndActuator("sensor_" + i, userId, appId, sensorNode.getId(), application);
        }
    }
    
    /**
     * Adds a sensor and actuator to the simulation with proper application linking.
     * @param id The ID suffix for the sensor and actuator.
     * @param userId The user ID.
     * @param appId The application ID.
     * @param gatewayId The gateway device ID.
     * @param application The application object.
     */
    private static void addSensorAndActuator(String id, int userId, String appId, int gatewayId, Application application) {
        double sensorLatency = CLOUD ? 50.0 : 1.0; // Higher latency in cloud mode
        double displayLatency = CLOUD ? 50.0 : 1.0; // Higher latency in cloud mode

        Sensor bpSensor = new Sensor("bp-" + id, "BP_SENSOR", userId, appId, 
                new DeterministicDistribution(SENSOR_TRANSMISSION_TIME));
        sensors.add(bpSensor);
        bpSensor.setGatewayDeviceId(gatewayId);
        bpSensor.setLatency(sensorLatency);
        bpSensor.setApp(application); // This is crucial for proper loop tracking

        Actuator display = new Actuator("display-" + id, userId, appId, "DISPLAY");
        actuators.add(display);
        display.setGatewayDeviceId(gatewayId);
        display.setLatency(displayLatency);
        display.setApp(application); // This ensures actuators are linked to the application
    }

    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw,
                                             int level, double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

        PowerHost host = new PowerHost(FogUtils.generateEntityId(),
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(10000),
                1000000,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                "x86", "Linux", "Xen", host, 10.0, 3.0,
                0.05, 0.001, 0.0);

        try {
            FogDevice device = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(Arrays.asList(host)), new LinkedList<Storage>(),
                    10, upBw, downBw, 0, ratePerMips);
            device.setLevel(level);
            return device;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);

        application.addAppModule("clientModule", 10);
        application.addAppModule("hypotensionDetector", 50);

        // Connecting the application modules with edges - using consistent format
        application.addAppEdge("BP_SENSOR", "clientModule", 1000, 500, "BP_SENSOR", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("clientModule", "hypotensionDetector", 2000, 500, "RAW_BP_DATA", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("hypotensionDetector", "clientModule", 500, 28, 1000, "PROCESSED_DATA", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("clientModule", "DISPLAY", 500, 500, "DISPLAY_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR);

        // Defining the input-output relationships of modules
        application.addTupleMapping("clientModule", "BP_SENSOR", "RAW_BP_DATA", new FractionalSelectivity(1.0));
        application.addTupleMapping("hypotensionDetector", "RAW_BP_DATA", "PROCESSED_DATA", new FractionalSelectivity(1.0));
        application.addTupleMapping("clientModule", "PROCESSED_DATA", "DISPLAY_UPDATE", new FractionalSelectivity(1.0));

        // Defining application loops to monitor latency - simplified structure
        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("BP_SENSOR");
            add("clientModule");
            add("hypotensionDetector");
            add("clientModule");
            add("DISPLAY");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
        }};
        application.setLoops(loops);

        return application;
    }

    private static void printResults() {
        System.out.println("=========================================");
        System.out.println("============== RESULTS ==================");
        System.out.println("=========================================");

        long execTime = Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime();
        System.out.println("EXECUTION TIME : " + execTime);

        System.out.println("=========================================");
        System.out.println("APPLICATION LOOP DELAYS");
        System.out.println("=========================================");
        Map<Integer, Double> loopDelays = TimeKeeper.getInstance().getLoopIdToCurrentAverage();
        if (loopDelays.isEmpty()) {
            System.out.println("No loop delay recorded.");
        } else {
            for (Map.Entry<Integer, Double> entry : loopDelays.entrySet()) {
                System.out.printf("Loop %d delay: %.2f ms%n", entry.getKey(), entry.getValue());
            }
        }

        System.out.println("=========================================");
        System.out.println("TUPLE CPU EXECUTION DELAY");
        System.out.println("=========================================");
        Map<String, Double> tupleDelays = TimeKeeper.getInstance().getTupleTypeToAverageCpuTime();
        for (Map.Entry<String, Double> entry : tupleDelays.entrySet()) {
            System.out.println(entry.getKey() + " ---> " + entry.getValue());
        }

        System.out.println("=========================================");
        System.out.println("ENERGY CONSUMED PER DEVICE");
        System.out.println("=========================================");
        for (FogDevice d : fogDevices) {
            System.out.println(d.getName() + " : Energy Consumed = " + d.getEnergyConsumption());
        }

        System.out.println("Cost of execution in cloud = " +
                fogDevices.stream().filter(d -> d.getName().equalsIgnoreCase("cloud"))
                        .mapToDouble(FogDevice::getTotalCost).sum());

        System.out.println("Total network usage = " +
                NetworkUsageMonitor.getNetworkUsage() / Config.MAX_SIMULATION_TIME);
    }
}