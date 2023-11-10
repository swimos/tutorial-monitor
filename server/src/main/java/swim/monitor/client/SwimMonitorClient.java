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
import swim.client.ClientRuntime;
import swim.uri.Uri;

public class SwimMonitorClient {

  public static final String HOST = System.getProperty("host", "warp://localhost:9001");
  public static final Uri HOST_URI = Uri.parse(HOST);

  public static void main(String[] args) {

    final ClientRuntime swimClient = new ClientRuntime();
    swimClient.start();
    final SystemInfo systemInfo = new SystemInfo();
    startProcessMonitor(swimClient, systemInfo);
  }

  private static void startProcessMonitor(final ClientRuntime swimClient, final SystemInfo systemInfo) {
    final ProcessMonitor processMonitor = new ProcessMonitor(swimClient, HOST_URI, systemInfo);
    swimClient.stage().execute(() -> {
      System.out.println("Starting ProcessMonitor");
      processMonitor.monitor();
    });
  }
}