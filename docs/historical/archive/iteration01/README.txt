# Iteration 1 Establish Basic Communication

## Overview
This iteration focuses on creating basic communication between three subsystems using threads. The goal is to verify that messages can be read, transmitted, and returned correctly between components.

## Setup and Execution Instructions
Requirements
Java JDK
IntelliJ IDEA

Opening the Project
Unzip the project folder.
Open IntelliJ IDEA.
Select File, Open.
Choose the root project folder.
Allow IntelliJ to load and index the project.

Running the Program
Locate the Main or launcher class in the org.example package.
Run the program using Run, Run 'Main'.

## Responsibility Breakdown
Pietro Adamvoski: Fire incident subsystem, and Scheduler subsystem
Avery Robertson: GUI, Drone subsystem, and communication between threads.
Adam Haddadin: Class diagram, Sequence diagram, README file.
Fareen Lavji: 

### Subsystems
The system has three independent subsystems, each implemented as its own thread:

1. Fire Incident Subsystem 
2. Drone Subsystem 
3. Scheduler Subsystem 

### Fire Incident Subsystem 
Reads fire incident events from an input file.
Each event follows the format:
  Time
  Zone ID
  Event Type
  Severity
Creates FireIncident objects from input data.
Sends incidents to the Scheduler using the IncidentReportChannel.
Receives acknowledgements forwarded back from the Scheduler.
Updates the GUI with incident and acknowledgement information.

### Drone Subsystem
Implemented by the DroneSubsystem class.
Communicates with the Scheduler through the DroneCommChannel.
Periodically sends requests indicating availability to perform tasks.
Receives forwarded incident information from the Scheduler.
Simulates handling the task and sends an acknowledgement back.

### Scheduler Subsystem
Implemented by the Scheduler class.
Acts as a pass through communication relay.
Receives FireIncident objects from the FireIncidentSubsystem by IncidentReportChannel.
Forwards incidents to the DroneSubsystem using DroneCommChannel.
Receives acknowledgements from the DroneSubsystem.
Forwards acknowledgements back to the FireIncidentSubsystem.

## Communication Flow 
1. FireIncidentSubsystem reads an event from the input file.
2. The event data is extracted from the input line and stored in a FireIncident object.
3. FireIncidentSubsystem sends the FireIncident to the Scheduler via IncidentReportChannel.
4. Scheduler forwards the incident to the DroneSubsystem via DroneCommChannel.
5. DroneSubsystem simulates processing and sends an acknowledgement message.
6. Scheduler forwards the acknowledgement back to the FireIncidentSubsystem.

This confirms round trip communication between all components.

## Concurrency Model
Each subsystem runs in its own thread or process.
Subsystems are designed to be independent.
