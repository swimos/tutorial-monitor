// Copyright 2015-2022 Swim.inc
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package swim.monitor.agent;

import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.JoinValueLane;
import swim.api.lane.MapLane;
import swim.api.lane.ValueLane;
import swim.structure.Value;
import swim.uri.Uri;

import java.util.Set;

public class ClusterAgent extends AbstractAgent {

  private static final Uri STATUS_MACHINE_LANE_URI = Uri.parse("status");
  private static final int STATUS_HISTORY_SIZE = 200;

  // Status lane for Cluster Agent
  @SwimLane("status")
  ValueLane<Value> status = this.<Value>valueLane()
          .didSet((nv, ov) -> {
            final long timestamp = nv.get("timestamp").longValue(0L);
            if (timestamp > 0L) {
              this.statusHistory.put(timestamp, nv.removed("timestamp"));
            }
          });

  @SwimLane("statusHistory")
  MapLane<Long, Value> statusHistory = this.<Long, Value>mapLane()
          .didUpdate((k, nv, ov) -> trimHistory());

  private void trimHistory() {
    final int dropCount = this.statusHistory.size() - STATUS_HISTORY_SIZE;
    if (dropCount > 0) {
      this.statusHistory.drop(dropCount);
    }
  }

  @SwimLane("addMachine")
  CommandLane<Value> addMachine = this.<Value>commandLane()
          .onCommand(v ->
                  this.machines.downlink(v)
                          .nodeUri(Uri.form().cast(v))
                          .laneUri(STATUS_MACHINE_LANE_URI)
                          .open());

  @SwimLane("machines")
  JoinValueLane<Value, Value> machines = this.<Value, Value>joinValueLane()
          .didUpdate((k, nv, ov) -> {
            updateLastTimestampStatus(nv);
            computeStatus();

            if (nv.get("disconnected").isDefined()) {
              this.machines.remove(k);
            }
          });

  private void computeStatus() {

    int machineCount = 0;

    long totalLatency = 0L;
    int latencyCount  = 0;

    double totalSystemLoad = 0.0, maxSystemLoad = 0.0;
    int systemLoadCount = 0;

    double avgCPUUsage = 0.0;
    double clusterAvgMemoryUsage = 0.0;

    float totalMemoryUsage = 0.0f, maxMemoryUsage = 0.0f;
    int memoryCount = 0;

    int clusterProcessCount = 0;

    final Set<Value> keys = this.machines.keySet();
    for (Value key : keys) {
      machineCount++;
      final Value machineStatus = this.machines.get(key);

      if (machineStatus.get("latency").isDefined()){
        latencyCount++;
        totalLatency += machineStatus.get("latency").longValue(0L);
      }

      if (machineStatus.get("average_system_load").isDefined()) {
        systemLoadCount++;
        double systemLoad = machineStatus.get("average_system_load").doubleValue(0);
        totalSystemLoad += systemLoad;
        if (systemLoad > maxSystemLoad) {
          maxSystemLoad = systemLoad;
        }
        avgCPUUsage = totalSystemLoad / systemLoadCount;
      }

      if (machineStatus.get("memory_usage").isDefined()) {
        memoryCount++;
        float memoryUsage = machineStatus.get("memory_usage").floatValue(0.0f);
        totalMemoryUsage += memoryUsage;
        if (memoryUsage > maxMemoryUsage) {
          maxMemoryUsage = memoryUsage;
        }
      }

      // Compute process count
      int machineProcessCount = machineStatus.get("process_count").intValue(0);
      clusterProcessCount = clusterProcessCount + machineProcessCount;
    }

    final long avgLatency = latencyCount == 0 ? 0 : totalLatency / latencyCount;
    final double avgSystemLoad = systemLoadCount == 0 ? 0 : totalSystemLoad / systemLoadCount;
    final float avgMemoryUsage = memoryCount == 0 ? 0 : totalMemoryUsage / memoryCount;

    this.status.set(
            this.status.get()
                    .updated("machine_count", machineCount)
                    .updated("average_latency", avgLatency)
                    .updated("average_system_load", avgSystemLoad)
                    .updated("max_system_load", maxSystemLoad)
                    .updated("average_memory_usage", avgMemoryUsage)
                    .updated("max_memory_usage", maxMemoryUsage)
                    .updated("average_cpu_usage", avgCPUUsage)
                    .updated("cluster_average_memory_usage", clusterAvgMemoryUsage)
                    .updated("cluster_process_count", clusterProcessCount)
    );
  }

  private void updateLastTimestampStatus(final Value update) {
    final long timestamp = update.get("timestamp").longValue(0L);
    if (timestamp > this.status.get().get("timestamp").longValue(0L)) {
      this.status.set(
              this.status.get().updated("timestamp", timestamp)
      );
    }
  }

  @Override
  public void didStart() {
    info(nodeUri() + ": didStart");
  }
}