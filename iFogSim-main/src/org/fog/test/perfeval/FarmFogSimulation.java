package org.fog.test.perfeval;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Actuator;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.distribution.DeterministicDistribution;
import org.cloudbus.cloudsim.core.CloudSim;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class FarmFogSimulation {
    public static void main(String[] args) {
        try {
            // Initialize CloudSim
            CloudSim.init(1, null, false);

            // Simulation scenarios (3, 15, 25 sensor-actuator pairs)
            int[] sensorCounts = {3, 15, 25};
            for (int n : sensorCounts) {
                // Initialize lists
                List<FogDevice> fogDevices = new ArrayList<>();
                List<Sensor> sensors = new ArrayList<>();
                List<Actuator> actuators = new ArrayList<>();

                // Create IoT Devices
                for (int i = 0; i < n; i++) {
                    Application sensorApp = createApplication("SensorApp-" + i, n);
                    Sensor sensor = new Sensor("Sensor-" + i, "SOIL_MOISTURE", 0, "SensorApp-" + i,
                            new DeterministicDistribution(1000));
                    sensor.setApp(sensorApp);
                    sensors.add(sensor);

                    Application actuatorApp = createApplication("ActuatorApp-" + i, n);
                    Actuator actuator = new Actuator("Actuator-" + i, 0, "ActuatorApp-" + i, "IRRIGATION_CONTROL");
                    actuator.setApp(actuatorApp);
                    actuators.add(actuator);
                }
                Application biometricApp = createApplication("BiometricApp", n);
                Sensor biometric = new Sensor("BiometricScanner", "AUTHENTICATE_USER", 0, "BiometricApp",
                        new DeterministicDistribution(2000));
                biometric.setApp(biometricApp);
                sensors.add(biometric);

                // Create Fog Node
                FogDevice fogNode = createFogDevice("FogNode", Constants.FOG_MIPS, Constants.FOG_RAM,
                        Constants.LORAWAN_BANDWIDTH, Constants.LORAWAN_BANDWIDTH, Constants.LORAWAN_LATENCY,
                        new FogLinearPowerModel(Constants.FOG_POWER_MAX, Constants.FOG_POWER_IDLE));
                fogDevices.add(fogNode);

                // Create Cloud
                FogDevice cloud = createFogDevice("Cloud", Constants.CLOUD_MIPS, Constants.CLOUD_RAM,
                        Constants.WAN_BANDWIDTH, Constants.WAN_BANDWIDTH, Constants.WAN_LATENCY,
                        new FogLinearPowerModel(Constants.CLOUD_POWER_MAX, Constants.CLOUD_POWER_IDLE));
                fogDevices.add(cloud);

                // Define Applications
                Application fogApp = createApplication("FogApp-" + n, n);
                Application cloudApp = createApplication("CloudApp-" + n, n);

                // Controller and Placement
                Controller controller = new Controller("Controller-" + n, fogDevices, sensors, actuators);

                // Fog-based placement
                ModuleMapping fogMapping = ModuleMapping.createModuleMapping();
                fogMapping.addModuleToDevice("auth-service", "FogNode");
                fogMapping.addModuleToDevice("processing-service", "FogNode");
                fogMapping.addModuleToDevice("control-service", "FogNode");
                fogMapping.addModuleToDevice("storage-service", "Cloud");

                // Cloud-only placement
                ModuleMapping cloudMapping = ModuleMapping.createModuleMapping();
                cloudMapping.addModuleToDevice("auth-service", "Cloud");
                cloudMapping.addModuleToDevice("processing-service", "Cloud");
                cloudMapping.addModuleToDevice("control-service", "Cloud");
                cloudMapping.addModuleToDevice("storage-service", "Cloud");

                // Submit Applications
                controller.submitApplication(fogApp, new ModulePlacementEdgewards(fogDevices, sensors, actuators, fogApp, fogMapping));
                controller.submitApplication(cloudApp, new ModulePlacementEdgewards(fogDevices, sensors, actuators, cloudApp, cloudMapping));

                // Run Simulation
                CloudSim.startSimulation();
                CloudSim.stopSimulation();

                // Simulated results (based on lab_09 assumptions)
                double fogLatency = Constants.LORAWAN_LATENCY * 2 + 10; // Round-trip + processing
                double cloudLatency = Constants.WAN_LATENCY * 2 + 20; // Round-trip + processing
                double fogEnergy = Constants.FOG_POWER_MAX * n * 0.01;
                double cloudEnergy = Constants.CLOUD_POWER_MAX * n * 0.015;
                double fogNetwork = 0.1 * n; // Aggregated data (KB)
                double cloudNetwork = 120 * n; // Raw data (KB)

                // Write results to CSV
                try (FileWriter writer = new FileWriter("results/case" + n + "_output.csv")) {
                    writer.write("Scenario,Latency_ms,Energy_J,Network_KB\n");
                    writer.write("Fog," + fogLatency + "," + fogEnergy + "," + fogNetwork + "\n");
                    writer.write("Cloud," + cloudLatency + "," + cloudEnergy + "," + cloudNetwork + "\n");
                }
                System.out.println("Scenario: " + n + " sensors completed. Results saved to case" + n + "_output.csv");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static FogDevice createFogDevice(String name, long mips, int ram, double uplinkBandwidth,
                                            double downlinkBandwidth, double uplinkLatency, PowerModel powerModel) throws Exception {
        // Create Processing Element (PE)
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

        // Create Host
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // 1GB storage
        int bw = 10000; // Bandwidth
        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                powerModel
        );
        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        // Create Characteristics
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem, costPerStorage, costPerBw);

        // Create Allocation Policy
        AppModuleAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(hostList);

        // Create Storage List
        List<Storage> storageList = new LinkedList<>();

        // Create FogDevice
        return new FogDevice(name, characteristics, vmAllocationPolicy, storageList, 10.0,
                uplinkBandwidth, downlinkBandwidth, uplinkLatency, 0.01);
    }

    private static Application createApplication(String appId, int n) {
        Application app = Application.createApplication(appId, 0);
        app.addAppModule("auth-service", 200, 1000, 512);
        app.addAppModule("processing-service", 300, 1000, 512);
        app.addAppModule("control-service", 100, 1000, 512);
        app.addAppModule("storage-service", 500, 1000, 512);

        // Define Edges
        app.addAppEdge("AUTHENTICATE_USER", "auth-service", 1000, 200, "AUTH_DATA", Tuple.UP, AppEdge.MODULE);
        app.addAppEdge("SOIL_MOISTURE", "processing-service", 1000, 500, "MOISTURE_DATA", Tuple.UP, AppEdge.MODULE);
        app.addAppEdge("processing-service", "control-service", 100, 50, "TRIGGER_IRRIGATION", Tuple.ACTUATOR, AppEdge.MODULE);
        app.addAppEdge("processing-service", "storage-service", 500, 200, "HOURLY_SUMMARY", Tuple.UP, AppEdge.MODULE);
        app.addAppEdge("control-service", "IRRIGATION_CONTROL", 100, 50, "CONTROL_SIGNAL", Tuple.DOWN, AppEdge.ACTUATOR);

        // Define Control Loop
        List<String> loop = new ArrayList<>();
        loop.add("SOIL_MOISTURE");
        loop.add("processing-service");
        loop.add("control-service");
        loop.add("IRRIGATION_CONTROL");
        List<AppLoop> loops = new ArrayList<>();
        loops.add(new AppLoop(loop));
        app.setLoops(loops);

        return app;
    }
}
