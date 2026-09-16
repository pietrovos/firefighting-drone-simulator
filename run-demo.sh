#!/usr/bin/env bash

set -euo pipefail

DEFAULT_DRONES=20

cleanup() {
  pkill -f "DroneSwarmSim.drone.DroneMain" 2>/dev/null || true
  pkill -f "DroneSwarmSim.scheduler.SchedulerMain" 2>/dev/null || true
  pkill -f "DroneSwarmSim.fire.FireIncidentMain" 2>/dev/null || true
}

show_error() {
  local message="$1"
  if command -v zenity >/dev/null 2>&1; then
    zenity --error --title="Run Demo" --text="$message"
  elif command -v kdialog >/dev/null 2>&1; then
    kdialog --error "$message" --title "Run Demo"
  else
    printf 'Error: %s\n' "$message" >&2
  fi
}

prompt_drone_count() {
  local input

  if command -v zenity >/dev/null 2>&1; then
    input=$(zenity --entry \
      --title="Run Demo" \
      --text="How many drones would you like to launch?\nLeave blank for ${DEFAULT_DRONES}." \
      --entry-text="${DEFAULT_DRONES}" || true)
  elif command -v kdialog >/dev/null 2>&1; then
    input=$(kdialog --inputbox "How many drones would you like to launch? Leave blank for ${DEFAULT_DRONES}." "${DEFAULT_DRONES}" || true)
  else
    printf 'How many drones would you like to launch? [default: %s] ' "${DEFAULT_DRONES}" >&2
    read -r input || true
  fi

  input="${input//[[:space:]]/}"
  if [[ -z "$input" ]]; then
    printf '%s\n' "$DEFAULT_DRONES"
    return
  fi

  if [[ "$input" =~ ^[0-9]+$ ]] && (( input > 0 )); then
    printf '%s\n' "$input"
    return
  fi

  show_error "Please enter a positive whole number of drones."
  exit 1
}

trap cleanup EXIT INT TERM

cleanup

drone_count=$(prompt_drone_count)

mvn compile

mvn exec:java -Dexec.mainClass="DroneSwarmSim.scheduler.SchedulerMain" &
scheduler_pid=$!

sleep 3

drone_pids=()
if (( drone_count == DEFAULT_DRONES )); then
  mvn exec:java -Dexec.mainClass="DroneSwarmSim.drone.DroneMain" &
  drone_pids+=("$!")
else
  for ((drone_id=0; drone_id<drone_count; drone_id++)); do
    mvn exec:java -Dexec.mainClass="DroneSwarmSim.drone.DroneMain" -Dexec.args="$drone_id" &
    drone_pids+=("$!")
  done
fi

sleep 2

mvn exec:java -Dexec.mainClass="DroneSwarmSim.fire.FireIncidentMain" &
fire_pid=$!

wait "$fire_pid"
wait "$scheduler_pid"
