iFogSim - Hypotension Detection Simulation
=========================================

This project is an iFogSim simulation comparing edge-ward and cloud-only deployments
for a Hypotension Detection IoT application.

Requirements
------------
- Java JDK 8 (iFogSim works best with Java 8)
- Eclipse or any Java IDE
- iFogSim dependencies (already included in the project)

Project Structure
-----------------
src/
  org/fog/test/perfeval/HypotensionDetection.java

How to Run in Eclipse
---------------------
1. Clone the repository:
   git clone https://github.com/Kornerstonecoker/ifogsim-hypotension.git

2. Open Eclipse → File → Import → Existing Projects into Workspace.

3. Select the cloned folder.

4. Ensure the JRE is set to Java 8 (Window → Preferences → Java → Installed JREs).

5. Right-click HypotensionDetection.java → Run As → Java Application.

Command-line Run
----------------
From the project root:

# Compile
javac -cp "lib/*;src" -d bin src/org/fog/test/perfeval/HypotensionDetection.java

# Run Edge-Ward (default)
java -cp "bin;lib/*" org.fog.test.perfeval.HypotensionDetection

# Run Cloud-Only
java -cp "bin;lib/*" org.fog.test.perfeval.HypotensionDetection true

(Use ":" instead of ";" in classpath on Linux/Mac.)

Modes
-----
- Edge-Ward: Processing on edge gateway (default)
- Cloud-Only: Processing in the cloud (true argument)

Output
------
Simulation prints:
- Execution time
- Application loop delays (if recorded)
- Tuple CPU execution delay
- Energy consumed per device
- Cloud execution cost
- Total network usage
