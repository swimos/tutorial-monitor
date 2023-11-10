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

package swim.monitor.client;

import oshi.SystemInfo;
import oshi.hardware.PowerSource;
import oshi.software.os.OSProcess;
import swim.api.ref.WarpRef;
import swim.structure.Record;
import swim.structure.Value;
import swim.uri.Uri;
import swim.uri.UriPattern;

import java.util.List;

import static oshi.software.os.OperatingSystem.ProcessFiltering.ALL_PROCESSES;
import static oshi.software.os.OperatingSystem.ProcessSorting.CPU_DESC;

public class ProcessMonitor extends Monitor {

  private static final String PULSE_INTERVAL_SEC_STR = System.getProperty("process.interval", "5");
  private static final int PULSE_INTERVAL_SEC = Integer.parseInt(PULSE_INTERVAL_SEC_STR);

  private static final UriPattern NODE_URI_PATTERN = UriPattern.parse("/machine/:id");
  private static final Uri LANE_URI = Uri.parse("addProcess");

  // Not thread-safe
  private long[] prevTicks;
  private Value osSystemInfo;
  private Value hwSystemInfo;

  public ProcessMonitor(final WarpRef warpRef, final Uri hostUri, final SystemInfo systemInfo) {
    super(warpRef, systemInfo, PULSE_INTERVAL_SEC * 1000L,
          hostUri,
          NODE_URI_PATTERN.apply(systemInfo.getOperatingSystem().getNetworkParams().getHostName()),
          LANE_URI);

    prevTicks = systemInfo.getHardware().getProcessor().getSystemCpuLoadTicks();

    Value memory = Record.create(1)
      .slot("total", systemInfo.getHardware().getMemory().getTotal());

    osSystemInfo = Record.create(5)
      .slot("manufacturer", systemInfo.getOperatingSystem().getManufacturer())
      .slot("family", systemInfo.getOperatingSystem().getFamily())
      .slot("version", systemInfo.getOperatingSystem().getVersionInfo().getVersion())
      .slot("bitness", systemInfo.getOperatingSystem().getBitness())
      .slot("process_count", systemInfo.getOperatingSystem().getProcessCount());

    hwSystemInfo = Record.create(7)
      .slot("manufacturer", systemInfo.getHardware().getComputerSystem().getManufacturer())
      .slot("model", systemInfo.getHardware().getComputerSystem().getModel())
      .slot("firmware_version", systemInfo.getHardware().getComputerSystem().getFirmware().getVersion())
      .slot("processor", Record.create(5)
        .slot("vendor", systemInfo.getHardware().getProcessor().getProcessorIdentifier().getVendor())
        .slot("name", systemInfo.getHardware().getProcessor().getProcessorIdentifier().getName())
        .slot("max_freq", systemInfo.getHardware().getProcessor().getMaxFreq())
        .slot("physical_count", systemInfo.getHardware().getProcessor().getPhysicalProcessorCount())
        .slot("logical_count", systemInfo.getHardware().getProcessor().getLogicalProcessorCount()))
      .slot("memory", memory);

    Value record = Record.create(3)
      .slot("timestamp", System.currentTimeMillis())
      .slot("os", osSystemInfo)
      .slot("hardware", hwSystemInfo);

    this.warpRef.command(hostUri, nodeUri, Uri.parse("addSystemInfo"), record);
  }

  @Override
  public void pulse() {
    final List<OSProcess> processes = systemInfo.getOperatingSystem().getProcesses(ALL_PROCESSES, CPU_DESC, 30);
    final long timestamp = System.currentTimeMillis();
    Record processStatus = Record.create(processes.size());
    for (final OSProcess process : processes) {
      processStatus.add(getProcessInfo(process, timestamp));
    }
    this.warpRef.command(hostUri, nodeUri, Uri.parse("addProcess"), processStatus);

    Value usage = getUsage(timestamp);
    this.warpRef.command(hostUri, nodeUri, Uri.parse("addUsage"), usage);
  }

  private Value getProcessInfo(final OSProcess process, final long timestamp) {
    return Record.create(10)
            .slot("timestamp", timestamp)
            .slot("pid", process.getProcessID())
            .slot("name", process.getName())
            .slot("user", process.getUser())
            .slot("user_id", process.getUserID())
            .slot("priority", process.getPriority())
            .slot("virtual_size", process.getVirtualSize())
            .slot("rss", process.getResidentSetSize())
            .slot("cpu_load", process.getProcessCpuLoadCumulative())
            .slot("uptime", process.getUpTime());
  }

  private Value getOSUtilization() {
    return Record.create(4)
    .slot("boot_time", systemInfo.getOperatingSystem().getSystemBootTime() * 1000)
    .slot("uptime", systemInfo.getOperatingSystem().getSystemUptime() * 1000)
    .slot("process_count", systemInfo.getOperatingSystem().getProcessCount())
    .slot("thread_count", systemInfo.getOperatingSystem().getThreadCount());
  }

  private Value getMemoryUtilization() {
    return Record.create(6)
    .slot("total", systemInfo.getHardware().getMemory().getTotal())
    .slot("available", systemInfo.getHardware().getMemory().getAvailable())
    .slot("swap_total", systemInfo.getHardware().getMemory().getVirtualMemory().getSwapTotal())
    .slot("swap_used", systemInfo.getHardware().getMemory().getVirtualMemory().getSwapUsed())
    .slot("virtual_max", systemInfo.getHardware().getMemory().getVirtualMemory().getVirtualMax())
    .slot("virtual_in_use", systemInfo.getHardware().getMemory().getVirtualMemory().getVirtualInUse());
  }

  private Value getProcessorUtilization() {
    return Record.create(2)
    .slot("max_frequency", systemInfo.getHardware().getProcessor().getMaxFreq())
    .slot("current_frequency", cpuFrequencyByProcessorValue(systemInfo.getHardware().getProcessor().getCurrentFreq()));
  }

  private Value getHardwareUtilization() {
    Value memoryInfo = getMemoryUtilization();
    Value processorInfo = getProcessorUtilization();

    final double averageSystemLoad = systemInfo.getHardware().getProcessor().getSystemCpuLoadBetweenTicks(prevTicks) * 100;
    prevTicks = systemInfo.getHardware().getProcessor().getSystemCpuLoadTicks();
    if (averageSystemLoad >= 0) {
      processorInfo = processorInfo.updated("average_system_load", averageSystemLoad);
    }
    final double cpuTemperature = systemInfo.getHardware().getSensors().getCpuTemperature();
    if (cpuTemperature != Double.NaN && cpuTemperature != 0) {
      processorInfo = processorInfo.updated("temperature", cpuTemperature);
    }

    return Record.create(4)
            .slot("memory", memoryInfo)
            .slot("processor", processorInfo)
            .slot("is_charging", isCharging(systemInfo.getHardware().getPowerSources()));
  }

  private Value getUsage(final long timestamp) {
    Value osInfo = getOSUtilization();
    Value hardwareInfo = getHardwareUtilization();

    return Record.create(3)
            .slot("timestamp", System.currentTimeMillis())
            .slot("os", osInfo)
            .slot("hardware", hardwareInfo);
  }

  private boolean isCharging(final List<PowerSource> powerSources) {
    for (PowerSource source : powerSources) {
      if (source.isCharging()) {
        return true;
      }
    }
    return false;
  }

  private Value cpuFrequencyByProcessorValue(final long[] cpuFrequencies) {
    final Value cpuFrequencyValue = Record.create(cpuFrequencies.length);
    for (int i = 0; i < cpuFrequencies.length; i++) {
      cpuFrequencyValue.updated(i + "", cpuFrequencies[i]);
    }
    return cpuFrequencyValue;
  }
}