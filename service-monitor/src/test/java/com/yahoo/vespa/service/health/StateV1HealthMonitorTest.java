// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.service.executor.RunletExecutor;
import com.yahoo.vespa.service.executor.RunletExecutorImpl;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StateV1HealthMonitorTest {
    @Test
    public void downThenUpThenDown() throws Exception {
        StateV1HealthClient client = mock(StateV1HealthClient.class);
        when(client.get()).thenReturn(HealthInfo.empty());

        StateV1HealthUpdater updater = new StateV1HealthUpdater(client);
        RunletExecutor executor = new RunletExecutorImpl(2);
        try (StateV1HealthMonitor monitor = new StateV1HealthMonitor(updater, executor, Duration.ofMillis(10))) {
            assertEquals(ServiceStatus.DOWN, monitor.getStatus());

            when(client.get()).thenReturn(HealthInfo.fromHealthStatusCode(HealthInfo.UP_STATUS_CODE));
            while (monitor.getStatus() != ServiceStatus.UP) {
                try { Thread.sleep(2); } catch (InterruptedException ignored) { }
            }

            when(client.get()).thenReturn(HealthInfo.fromException(new IllegalStateException("foo")));
            while (monitor.getStatus() != ServiceStatus.DOWN) {
                try { Thread.sleep(2); } catch (InterruptedException ignored) { }
            }
        }
    }
}