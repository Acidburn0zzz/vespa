// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.EndpointResult;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.20
 */
public class OperationProcessorTest {

    final Queue<Result> queue = new ArrayDeque<>();
    final Document doc1 = new Document("doc:a:b", "data doc 1", null /* context */);
    final Document doc1b = new Document("doc:a:b", "data doc 1b", null /* context */);
    final Document doc2 = new Document("doc:a:b2", "data doc 2", null /* context */);
    final Document doc3 = new Document("doc:a:b3", "data doc 3", null /* context */);

    @Test
    public void testBasic() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .build();

        OperationProcessor q = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);


        q.resultReceived(new EndpointResult("foo", new Result.Detail(null)), 0);
        assertThat(queue.size(), is(0));


        q.sendDocument(doc1);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("d"))), 3);
        assertThat(queue.size(), is(1));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("e"))), 0);
        assertThat(queue.size(), is(1));

        //check a, b, c, d
        Result aggregated = queue.poll();
        assertThat(aggregated.getDocumentId(), equalTo("doc:a:b"));
        assertThat(aggregated.getDetails().size(), is(4));
        assertThat(aggregated.getDetails().get(0).getEndpoint().getHostname(), equalTo("a"));
        assertThat(aggregated.getDetails().get(1).getEndpoint().getHostname(), equalTo("b"));
        assertThat(aggregated.getDetails().get(2).getEndpoint().getHostname(), equalTo("c"));
        assertThat(aggregated.getDetails().get(3).getEndpoint().getHostname(), equalTo("d"));
        assertThat(aggregated.getDocumentDataAsCharSequence().toString(), is("data doc 1"));

        assertThat(queue.size(), is(0));


        q.sendDocument(doc2);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("d"))), 3);
        assertThat(queue.size(), is(1));

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("e"))), 0);
        assertThat(queue.size(), is(1));

        //check a, b, c, d
        aggregated = queue.poll();
        assertThat(aggregated.getDocumentId(), equalTo("doc:a:b2"));
        assertThat(aggregated.getDetails().size(), is(4));
        assertThat(aggregated.getDetails().get(0).getEndpoint().getHostname(), equalTo("a"));
        assertThat(aggregated.getDetails().get(1).getEndpoint().getHostname(), equalTo("b"));
        assertThat(aggregated.getDetails().get(2).getEndpoint().getHostname(), equalTo("c"));
        assertThat(aggregated.getDetails().get(3).getEndpoint().getHostname(), equalTo("d"));
        assertThat(aggregated.getDocumentDataAsCharSequence().toString(), is("data doc 2"));

        assertThat(queue.size(), is(0));
    }

    @Test
    public void testBlockingOfOperationsTwoEndpoints() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .setConnectionParams(new ConnectionParams.Builder().build())
                .build();
        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        operationProcessor.sendDocument(doc1);
        operationProcessor.sendDocument(doc1b);

        assertThat(queue.size(), is(0));
        // Only one operations should be in flight.
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(1));
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        assertThat(queue.size(), is(0));
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(1));
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 1);
        assertThat(queue.size(), is(1));
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(1));
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        assertThat(queue.size(), is(1));
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(1));
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 1);
        assertThat(queue.size(), is(2));
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(0));
        // This should have no effect.
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 1);
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 1);
        assertThat(queue.size(), is(2));
    }

    @Test
    public void testBlockingOfOperationsToSameDocIdWithTwoOperations() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .setConnectionParams(new ConnectionParams.Builder().build())
                .build();

        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        operationProcessor.sendDocument(doc1);
        operationProcessor.sendDocument(doc1b);

        assertThat(queue.size(), is(0));
        // Only one operations should be in flight.
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(1));
        assertThat(operationProcessor.oldestIncompleteResultId(), is(Optional.of(doc1.getOperationId())));
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        assertThat(queue.size(), is(1));
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(1));
        assertThat(operationProcessor.oldestIncompleteResultId(), is(Optional.of(doc1b.getOperationId())));
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        assertThat(queue.size(), is(2));
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(0));
        assertThat(operationProcessor.oldestIncompleteResultId(), is(Optional.empty()));
        // This should have no effect.
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        assertThat(queue.size(), is(2));
    }

    @Test
    public void testBlockingOfOperationsToSameDocIdMany() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .setConnectionParams(new ConnectionParams.Builder().build())
                .build();

        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        Queue<Document> documentQueue = new ArrayDeque<>();
        for (int x = 0; x < 100; x++) {
            Document document = new Document("doc:a:b", String.valueOf(x), null /* context */);
            operationProcessor.sendDocument(document);
            documentQueue.add(document);
        }

        for (int x = 0; x < 100; x++) {
            assertThat(queue.size(), is(x));
            // Only one operations should be in flight.
            assertThat(operationProcessor.getIncompleteResultQueueSize(), is(1));
            Document document = documentQueue.poll();
            operationProcessor.resultReceived(new EndpointResult(document.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
            assertThat(queue.size(), is(x + 1));
            if (x < 99) {
                assertThat(operationProcessor.getIncompleteResultQueueSize(), is(1));
            } else {
                assertThat(operationProcessor.getIncompleteResultQueueSize(), is(0));
            }
        }
    }

    @Test
    public void testMixOfBlockingAndNonBlocking() {
        Endpoint endpoint = Endpoint.create("host");
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(endpoint).build())
                .setConnectionParams(new ConnectionParams.Builder().build())
                .build();

        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        operationProcessor.sendDocument(doc1);
        operationProcessor.sendDocument(doc1b); // Blocked
        operationProcessor.sendDocument(doc2);
        operationProcessor.sendDocument(doc3);

        assertThat(queue.size(), is(0));
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(3));
        assertThat(operationProcessor.oldestIncompleteResultId(), is(Optional.of(doc1.getOperationId())));
        // This should have no effect since it should not be sent.
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(endpoint)), 0);
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(3));
        assertThat(operationProcessor.oldestIncompleteResultId(), is(Optional.of(doc1.getOperationId())));

        operationProcessor.resultReceived(new EndpointResult(doc3.getOperationId(), new Result.Detail(endpoint)), 0);
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(2));
        assertThat(operationProcessor.oldestIncompleteResultId(), is(Optional.of(doc1.getOperationId())));
        operationProcessor.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(endpoint)), 0);
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(1));
        assertThat(operationProcessor.oldestIncompleteResultId(), is(Optional.of(doc1.getOperationId())));
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(endpoint)), 0);
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(1));
        assertThat(operationProcessor.oldestIncompleteResultId(), is(Optional.of(doc1b.getOperationId())));
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(endpoint)), 0);
        assertThat(operationProcessor.getIncompleteResultQueueSize(), is(0));
        assertThat(operationProcessor.oldestIncompleteResultId(), is(Optional.empty()));
    }

    @Test
    public void assertThatDuplicateResultsFromOneClusterWorks() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .build();

        OperationProcessor q = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        q.sendDocument(doc1);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("b"))), 0);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("c"))), 0);
        assertThat(queue.size(), is(0));
    }

    @Test
    public void testMultipleDuplicateDocIds() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .build();

        OperationProcessor q = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        q.sendDocument(doc1);
        assertThat(queue.size(), is(0));
        q.sendDocument(doc2);
        assertThat(queue.size(), is(0));
        q.sendDocument(doc3);

        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertThat(queue.size(), is(0));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertThat(queue.size(), is(1));

        q.resultReceived(new EndpointResult(doc3.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertThat(queue.size(), is(1));

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertThat(queue.size(), is(1));

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertThat(queue.size(), is(1));

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertThat(queue.size(), is(2));

        q.resultReceived(new EndpointResult(doc3.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertThat(queue.size(), is(2));

        q.resultReceived(new EndpointResult(doc3.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertThat(queue.size(), is(2));

        q.resultReceived(new EndpointResult(doc3.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertThat(queue.size(), is(3));

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertThat(queue.size(), is(3));
        assertThat(queue.remove().getDocumentDataAsCharSequence().toString(), is("data doc 1"));
        assertThat(queue.remove().getDocumentDataAsCharSequence().toString(), is("data doc 2"));
        assertThat(queue.remove().getDocumentDataAsCharSequence().toString(), is("data doc 3"));

    }

    @Test
    public void testWaitBlocks() throws InterruptedException {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("host")).build())
                .build();

        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(1, 1, null, null),
                (docId, documentResult) -> {},
                sessionParams, null);

        operationProcessor.sendDocument(doc1);

        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);

        Thread shouldWait = new Thread(()-> {
            started.countDown();
            operationProcessor.sendDocument(doc2);
            done.countDown();
        });
        shouldWait.start();
        started.await();
        // We want the test to pass fast so we only wait 40mS to see that it is blocking. This might lead to
        // some false positives, but that is ok.
        assertThat(done.await(40, TimeUnit.MILLISECONDS), is(false));
        operationProcessor.resultReceived(
                new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("d"))), 0);
        assertThat(done.await(120, TimeUnit.SECONDS), is(true));

    }

    @Test
    public void testSendsResponseToQueuedDocumentOnClose() throws InterruptedException {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("#$#")).build())
                .build();

        ScheduledThreadPoolExecutor executor = mock(ScheduledThreadPoolExecutor.class);
        when(executor.awaitTermination(anyLong(), any())).thenReturn(true);

        CountDownLatch countDownLatch = new CountDownLatch(3);

        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(19, 19, null, null),
                (docId, documentResult) -> {
                    countDownLatch.countDown();
                },
                sessionParams, executor);

        // Will fail due to bogus host name, but will be retried.
        operationProcessor.sendDocument(doc1);
        operationProcessor.sendDocument(doc2);
        operationProcessor.sendDocument(doc3);

        // Will create fail results.
        operationProcessor.close();
        countDownLatch.await();
    }
}
