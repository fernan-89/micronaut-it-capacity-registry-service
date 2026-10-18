package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.context.event.StartupEvent;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CapacityIndexInitializerTest {

    private final MongoClient client = mock(MongoClient.class);

    @Test
    @DisplayName("startup creates the unique policy name, the unique sample key, the sample list index and the expiry (TTL) index")
    void indexes() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> policies = mock(MongoCollection.class);
        MongoCollection<Document> samples = mock(MongoCollection.class);
        when(client.getDatabase("tenant_cap")).thenReturn(database);
        when(database.getCollection("capacity_policies")).thenReturn(policies);
        when(database.getCollection("capacity_samples")).thenReturn(samples);
        when(policies.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));
        when(samples.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new CapacityIndexInitializer(client, "mongodb://mongo:27017/tenant_cap", 400).onApplicationEvent(mock(StartupEvent.class));

        ArgumentCaptor<IndexOptions> policyOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(policies).createIndex(any(), policyOptions.capture());
        assertTrue(policyOptions.getValue().isUnique());
        assertEquals("organisationId_1_name_1", policyOptions.getValue().getName());
        ArgumentCaptor<IndexOptions> sampleOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(samples, times(3)).createIndex(any(), sampleOptions.capture());
        assertTrue(sampleOptions.getAllValues().get(0).isUnique());
        assertEquals("organisationId_1_policyId_1_measuredAt_1", sampleOptions.getAllValues().get(0).getName());
        assertFalse(sampleOptions.getAllValues().get(1).isUnique());
        assertEquals("organisationId_1_policyId_1_measuredAt_-1", sampleOptions.getAllValues().get(1).getName());
        assertEquals("measuredAt_1", sampleOptions.getAllValues().get(2).getName());
        assertEquals(400L * 24 * 3600, sampleOptions.getAllValues().get(2).getExpireAfter(TimeUnit.SECONDS));
        assertEquals(null, sampleOptions.getAllValues().get(1).getExpireAfter(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated; the URI may omit the database; arguments are null-checked")
    void failOpen() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase("thinklab_it_capacity_registry_db")).thenReturn(database);
        when(database.getCollection(any(String.class))).thenReturn(collection);
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.error(new MongoTimeoutException("no server")))
                .thenReturn(Mono.error(new IllegalStateException("rejected"))).thenReturn(Mono.just("ok"));
        var initializer = new CapacityIndexInitializer(client, "mongodb://mongo:27017", 400);

        initializer.onApplicationEvent(mock(StartupEvent.class));

        verify(collection, times(4)).createIndex(any(), any(IndexOptions.class));
        assertThrows(NullPointerException.class, () -> initializer.onApplicationEvent(null));
        assertThrows(NullPointerException.class, () -> new CapacityIndexInitializer(null, "mongodb://mongo:27017", 400));
        assertThrows(NullPointerException.class, () -> new CapacityIndexInitializer(client, null, 400));
    }
}
