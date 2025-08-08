import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.TimeKeeper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SmartHomeSimulation {
    public static void main(String[] args) {
        try {
            String config = args.length > 0 ? args[0] : "edge_ward";
            // Initialize lists
            List<FogDevice> fogDevices = new ArrayList<>();
            List<Sensor> sensors = new ArrayList<>();

            // Create Sensors
            for (int i = 1; i <= 5; i++) {
                sensors.add(new Sensor("Sensor-s" + i, "TEMPERATURE", 0, 1000, new HashMap<>()));
            }

            // Create Devices
            FogDevice edge = new FogDevice("raspberry_pi_4", 1500, 4096, 32000, 5, 0,
                    new FogLinearPowerModel(5, 10), 0.0);
            FogDevice cloud = new FogDevice("aws_ec2_t3_micro", 2000, 1024, 1000000, 10, 0,
                    new FogLinearPowerModel(10, 20), 0.0);
            fogDevices.add(edge);
            if (!config.equals("edge_ward")) {
                fogDevices.add(cloud);
            }

            // Define Applications
            Application app = createApplication("SmartHomeApp-" + config, config);

            // Controller and Placement
            Controller controller = new Controller("Controller-" + config, fogDevices, sensors, new ArrayList<>());
            ModuleMapping mapping = ModuleMapping.createModuleMapping();
            if (config.equals("edge_ward")) {
                mapping.addModuleToDevice("sensor_reader", "raspberry_pi_4");
                mapping.addModuleToDevice("query_processor", "raspberry_pi_4");
            } else if (config.equals("cloud_only")) {
                mapping.addModuleToDevice("sensor_reader", "aws_ec2_t3_micro");
                mapping.addModuleToDevice("query_processor", "aws_ec2_t3_micro");
                mapping.addModuleToDevice("cloud_storage", "aws_ec2_t3_micro"); // Simulated S3
            } else { // edge_cloud
                mapping.addModuleToDevice("sensor_reader", "raspberry_pi_4");
                mapping.addModuleToDevice("query_processor_edge", "raspberry_pi_4");
                mapping.addModuleToDevice("query_processor_cloud", "aws_ec2_t3_micro");
                mapping.addModuleToDevice("cloud_storage", "aws_ec2_t3_micro"); // Simulated S3
            }

            // Submit and Run
            controller.submitApplication(app, new ModulePlacementEdgewards(fogDevices, sensors, new ArrayList<>(), app, mapping));
            TimeKeeper.getInstance().setSimulationStartTime(System.currentTimeMillis());
            controller.startSimulation();

            // Output results to text file (simulated)
            System.out.println("Scenario: " + config + " completed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Application createApplication(String appId, String config) {
        Application app = new Application(appId, 0);
        app.addAppModule("sensor_reader", 10); // Simulates MQTT data collection
        if (config.equals("edge_ward") || config.equals("cloud_only")) {
            app.addAppModule("query_processor", 50); // Simulates top-k query processing
            app.addAppEdge("TEMPERATURE", "sensor_reader", 1000, 8, "raw_temp", AppEdge.MODULE); // 8 bytes/10s
            app.addAppEdge("raw_temp", "query_processor", 1000, 8, "temp_data", AppEdge.MODULE);
            app.addAppEdge("temp_data", "query_processor", 100, 24, "top_k_results", AppEdge.MODULE); // 24 bytes for top-3

            List<String> loop = new ArrayList<>();
            loop.add("TEMPERATURE");
            loop.add("sensor_reader");
            loop.add("query_processor");
            loop.add("local_display");
            app.addAppLoop(new AppLoop(loop));
        } else { // edge_cloud
            app.addAppModule("query_processor_edge", 50); // Edge-side processing
            app.addAppModule("query_processor_cloud", 50); // Cloud-side processing
            app.addAppModule("cloud_storage", 20); // Simulates S3 storage
            app.addAppEdge("TEMPERATURE", "sensor_reader", 1000, 8, "raw_temp", AppEdge.MODULE); // MQTT input
            app.addAppEdge("raw_temp", "query_processor_edge", 1000, 8, "temp_data", AppEdge.MODULE);
            app.addAppEdge("temp_data", "query_processor_edge", 100, 24, "top_k_results_edge", AppEdge.MODULE);
            app.addAppEdge("temp_data", "query_processor_edge", 1000, 8, "temp_data_cloud", AppEdge.MODULE); // To cloud
            app.addAppEdge("temp_data_cloud", "query_processor_cloud", 100, 24, "top_k_results_cloud", AppEdge.MODULE);
            app.addAppEdge("temp_data", "query_processor_edge", 60000, 40, "agg_data", AppEdge.MODULE); // 40 bytes/60s
            app.addAppEdge("agg_data", "cloud_storage", 100, 40, "stored_data", AppEdge.MODULE); // Simulated S3 upload

            List<String> edgeLoop = new ArrayList<>();
            edgeLoop.add("TEMPERATURE");
            edgeLoop.add("sensor_reader");
            edgeLoop.add("query_processor_edge");
            edgeLoop.add("local_display");
            app.addAppLoop(new AppLoop(edgeLoop));

            List<String> cloudLoop = new ArrayList<>();
            cloudLoop.add("TEMPERATURE");
            cloudLoop.add("sensor_reader");
            cloudLoop.add("query_processor_edge");
            cloudLoop.add("query_processor_cloud");
            cloudLoop.add("local_display");
            app.addAppLoop(new AppLoop(cloudLoop));

            List<String> storageLoop = new ArrayList<>();
            storageLoop.add("TEMPERATURE");
            storageLoop.add("sensor_reader");
            storageLoop.add("query_processor_edge");
            storageLoop.add("cloud_storage");
            storageLoop.add("s3_storage");
            app.addAppLoop(new AppLoop(storageLoop));
        }

        return app;
    }
}