package com.github.hekonsek.vertx.pipes.marketplace.function.elasticsearch;

import com.github.hekonsek.vertx.pipes.Pipe;
import com.github.hekonsek.vertx.pipes.SimpleFunctionRegistry;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.debezium.kafka.KafkaCluster;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.producer.impl.KafkaProducerRecordImpl;
import org.apache.kafka.common.utils.Bytes;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

import static com.github.hekonsek.vertx.pipes.Pipe.pipe;
import static com.github.hekonsek.vertx.pipes.Pipes.pipes;
import static com.github.hekonsek.vertx.pipes.internal.KafkaProducerBuilder.pipeProducer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ElasticSearchFunctionTest {

    static {
        try {
            new KafkaCluster().withPorts(2181, 9092).usingDirectory(Files.createTempDir()).deleteDataPriorToStartup(true).addBrokers(1).startup();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void shouldPersistEvent() throws InterruptedException {
        String index = UUID.randomUUID().toString();
        String type = "type";
        SimpleFunctionRegistry functionRegistry = new SimpleFunctionRegistry();
        ElasticSearchFunction elasticSearchFunction = new ElasticSearchFunction().targetType(event -> index + "/" + type);
        functionRegistry.registerFunction("elasticsearch", elasticSearchFunction);

        Pipe pipe = pipe("elasticsearch", UUID.randomUUID().toString(), "elasticsearch");
        pipes(Vertx.vertx(), functionRegistry).startPipe(pipe);

        Map<String, Object> event = ImmutableMap.of("foo", "bar");
        pipeProducer(Vertx.vertx()).write(new KafkaProducerRecordImpl<>(pipe.getSource(), "key", new Bytes(Json.encode(event).getBytes())));

        Settings settings = Settings.builder().put("cluster.name", "default").build();
        TransportClient client = new PreBuiltTransportClient(settings).addTransportAddress(new TransportAddress(new InetSocketAddress("localhost", 9300)));

        Thread.sleep(2000);
        await().untilAsserted(() -> {
            Map<String, Object> savedEvent = client.prepareSearch(index).setTypes(type).get().getHits().getAt(0).getSourceAsMap();
            assertThat(savedEvent).isEqualTo(event);
        });
    }

}
