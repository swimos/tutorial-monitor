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

package swim.monitor.model;

import java.time.Duration;
import swim.structure.Item;
import swim.structure.Value;

public class StatusComputer {

  public static Value computeSeverityFromStatus(final Value currentStatus) {
    float severity = 0.0f;

    if (currentStatus.get("disconnected").isDefined()) {
      return currentStatus;
    }

    if (!currentStatus.get("updating").booleanValue(true)) {
      severity = 2.0f;
    } else {
      final long latency = Math.min(currentStatus.get("latency").longValue(0L), 200L);
      final float memoryUsage = currentStatus.get("memory_usage").floatValue(0.0f);
      final double avgSystemLoad = currentStatus.get("average_system_load").doubleValue(0.0);

      severity = (((float) latency / 100f) + (memoryUsage) + ((float) avgSystemLoad  / 100f)) / 2f;
    }

    return currentStatus.updated("severity", severity);

  }

  public static Value computeStatusFromUsage(Value currentStatus, final Value usage) {

    // Memory
    final Value memory = usage.get("hardware").get("memory");
    final long available = memory.get("available").longValue(0);
    final long total = memory.get("total").longValue(0);
    final float memoryUsage = 1.0f - (total == 0L ? 0.0f : (float) available / (float) total);
    final long swapTotal = memory.get("swap_total").longValue(0L);
    final long swapUsed = memory.get("swap_used").longValue(0L);
    final float swapUsage = (float) swapUsed / (float) swapTotal;

    // CPU
    final Value processor = usage.get("hardware").get("processor");
    final long maxProcessorFrequency = processor.get("max_frequency").longValue(0L);
    final Value currentFrequenciesValue = processor.get("current_frequency");
    long totalFrequency = 0L;
    long currentMaxFrequency = 0L;
    for (final Item currentFrequencyItem : currentFrequenciesValue) {
      final long currentFrequency = currentFrequencyItem.longValue(0L);
      totalFrequency += currentFrequency;
      if (currentFrequency > currentMaxFrequency) {
        currentMaxFrequency = currentFrequency;
      }
    }
    final float meanProcessorUsage = (float) (totalFrequency / currentFrequenciesValue.length()) / (float) (maxProcessorFrequency);
    final float maxProcessorUsage = (float) currentMaxFrequency / (float) maxProcessorFrequency;

    if (processor.get("average_system_load").isDefined()) {
      final double averageSystemLoad = processor.get("average_system_load").doubleValue(-1);
      currentStatus = currentStatus.updated("average_system_load", averageSystemLoad);
    }

    if (processor.get("temperature").isDefined()) {
      final double cpuTemperature = processor.get("temperature").doubleValue(Double.NaN);
      currentStatus = currentStatus.updated("cpu_temperature", cpuTemperature);
    }

    long upTime = usage.get("os").get("uptime").longValue(0L);
    Duration duration = Duration.ofMillis(upTime);
    long DD = duration.toDays();
    long HH = duration.toHoursPart();
    long MM = duration.toMinutesPart();
    long SS = duration.toSecondsPart();
    String timeInDDHHMMSS = String.format("%02d:%02d:%02d:%02d", DD, HH, MM, SS);

    return currentStatus
            .updated("raw_uptime", upTime)
            .updated("formatted_uptime", timeInDDHHMMSS)
            .updated("memory_usage", memoryUsage)
            .updated("swap_usage", swapUsage)
            .updated("max_processor_usage", maxProcessorUsage)
            .updated("mean_processor_usage", meanProcessorUsage)
            .updated("is_charging", usage.get("hardware").get("is_charging").booleanValue())
            .updated("process_count", usage.get("os").get("process_count").intValue(0));
  }
}