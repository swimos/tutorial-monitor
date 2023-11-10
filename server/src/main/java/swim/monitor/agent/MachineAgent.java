// Copyright 2015-2023 Swim.inc
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.MapLane;
import swim.api.lane.ValueLane;
import swim.concurrent.TimerRef;
import swim.monitor.model.StatusComputer;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Value;
import swim.uri.Uri;
import swim.uri.UriPattern;

public class MachineAgent extends AbstractAgent {

  private static final UriPattern CLUSTER_URI_PATTERN = UriPattern.parse("/cluster/:id");
  private static final Uri ADD_MACHINE_CLUSTER_LANE_URI = Uri.parse("addMachine");

  private static final int STATUS_HISTORY_SIZE = 200;

  private static final long DISCONNECT_WARNING_TIME = 10000L;
  private static final long DISCONNECT_TIME = 7200000L;
  private TimerRef disconnectWarningTimer;
  private TimerRef disconnectTimer;

  // Status Lane for Machine Agent
  @SwimLane("status")
  ValueLane<Value> status = this.<Value>valueLane()
          .willSet(StatusComputer::computeSeverityFromStatus)
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

  @SwimLane("addUsage")
  CommandLane<Value> addUsage = this.<Value>commandLane()
          .onCommand(v -> this.usage.set(v));

  @SwimLane("usage")
  ValueLane<Value> usage = this.<Value>valueLane()
          .didSet((newValue, oldValue) -> {
            updateLastTimestampStatus(newValue);
            this.status.set(StatusComputer.computeStatusFromUsage(this.status.get(), newValue));
          });

  @SwimLane("addProcess")
  CommandLane<Value> addProcess = this.<Value>commandLane()
          .didCommand(v -> {
            reconcileProcesses(v);
          });

  private void reconcileProcesses(Value v) {
    final Iterator<Item> it = v.iterator();
    Set<Integer> currentPids = new HashSet<>();
    while (it.hasNext()) {
      final Value value = it.next().toValue();
      int pid = value.get("pid").intValue(-1);
      if (pid > 0) {
        processes.put(pid, value);
        currentPids.add(pid);
      }
    }
    for(int oldProcessId: processes.keySet()) {
      if (!currentPids.contains(oldProcessId)) {
        processes.remove(oldProcessId);
      }
    }
  }

  @SwimLane("processes")
  MapLane<Integer, Value> processes = this.<Integer, Value>mapLane()
          .didUpdate((k, nv, ov) -> {
            updateLastTimestampStatus(nv);
          });

  @SwimLane("addSystemInfo")
  CommandLane<Value> addSystemInfo = this.<Value>commandLane()
          .onCommand(v -> this.systemInfo.set(v));

  @SwimLane("systemInfo")
  ValueLane<Value> systemInfo = this.<Value>valueLane()
          .didSet((newValue, oldValue) -> {
            updateLastTimestampStatus(newValue);
            // command(CLUSTER_URI_PATTERN.apply(newValue.get("cluster_id").stringValue()), ADD_MACHINE_CLUSTER_LANE_URI, Uri.form().mold(nodeUri()).toValue());
            command(CLUSTER_URI_PATTERN.apply("default"), ADD_MACHINE_CLUSTER_LANE_URI, Uri.form().mold(nodeUri()).toValue());
          });

  private void updateLastTimestampStatus(final Value update) {
    final long timestamp = update.get("timestamp").longValue(0L);
    if (timestamp > this.status.get().get("timestamp").longValue(0L)) {
      resetDisconnectWarningTimer();
      final long latency = System.currentTimeMillis() - timestamp;
      this.status.set(
              this.status.get().updated("timestamp", timestamp).updated("latency", latency).updated("updating", true).removed("disconnected")
      );
    }
  }

  private void resetDisconnectWarningTimer() {
    // There has been an update - reset the timers
    cancelDisconnectTimer();
    cancelDisconnectWarningTimer();
    this.disconnectWarningTimer = setTimer(DISCONNECT_WARNING_TIME, this::setDisconnectWarning);
  }

  private void setDisconnectWarning() {
    // Client had not updated in given time
    warn(nodeUri() + ": noUpdate");
    this.status.set(Record.create(2).slot("timestamp", System.currentTimeMillis()).slot("updating", false));
    cancelDisconnectWarningTimer();
    startDisconnectTimer();
  }

  private void startDisconnectTimer() {
    cancelDisconnectTimer();
    this.disconnectTimer = setTimer(DISCONNECT_TIME, this::disconnect);
  }

  private void cancelDisconnectWarningTimer() {
    if (this.disconnectWarningTimer != null) {
      this.disconnectWarningTimer.cancel();
    }
    this.disconnectWarningTimer = null;
  }

  private void cancelDisconnectTimer() {
    if (this.disconnectTimer != null) {
      this.disconnectTimer.cancel();
    }
    this.disconnectTimer = null;
  }

  private void disconnect() {
    this.status.set(Record.create(2).slot("timestamp", System.currentTimeMillis()).slot("disconnected", true));
    info(nodeUri() + ": disconnected");
  }

  @Override
  public void didStart() {
    info(nodeUri() + ": didStart");
  }

  @Override
  public void willStop() {
    info(nodeUri() + ": willStop");
  }
}