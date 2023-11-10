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
import swim.api.ref.WarpRef;
import swim.uri.Uri;

public abstract class Monitor {

  protected final SystemInfo systemInfo;
  protected final WarpRef warpRef;
  private final long pulseInterval;
  protected final Uri hostUri;
  protected final Uri nodeUri;
  protected final Uri laneUri;
  private static final long START_PAUSE = 5000L;

  public Monitor(final WarpRef warpRef, final SystemInfo systemInfo, final long pulseInterval, final Uri hostUri, final Uri nodeUri, final Uri laneUri) {
    this.warpRef = warpRef;
    this.systemInfo = systemInfo;
    this.pulseInterval = pulseInterval;
    this.hostUri = hostUri;
    this.nodeUri = nodeUri;
    this.laneUri = laneUri;
  }

  public void monitor() {
    sleep(START_PAUSE);
    do {
      pulse();
      sleep(pulseInterval);
    } while (true);
  }

  private void sleep(long duration) {
    try {
      Thread.sleep(duration);
    } catch (Exception e) {

    }
  }

  public abstract void pulse();
}