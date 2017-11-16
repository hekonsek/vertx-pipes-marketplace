package com.github.hekonsek.vertx.pipes.marketplace.function.elasticsearch;

import com.github.hekonsek.vertx.pipes.EventExpression;
import com.github.hekonsek.vertx.pipes.StartableFunction;
import io.vertx.core.eventbus.Message;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static com.github.hekonsek.vertx.pipes.Pipes.HEADER_KEY;
import static io.vertx.core.json.Json.encode;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.elasticsearch.common.xcontent.XContentType.JSON;

public class ElasticSearchFunction implements StartableFunction {

    private String clusterName = "default";

    private List<String> dateFields = emptyList();

    private EventExpression<String> targetType;

    // Internal collaborators

    private TransportClient client;

    private DateFormat dateFormat;

    @Override public void start() {
        Settings settings = Settings.builder().put("cluster.name", clusterName).build();
        client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new TransportAddress(new InetSocketAddress("localhost", 9300)));

        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    @Override public void handle(Message<Map<String, Object>> event) {
        String key = event.headers().get(HEADER_KEY);
        dateFields.forEach(field ->
                event.body().put(field, dateFormat.format(new Date((Long) event.body().get(field))))
        );
        String json = encode(event.body());
        String targetType = this.targetType.evaluate(event);
        String[] targetTypeElements = targetType.split("/");
        IndexRequestBuilder indexRequest =client.prepareIndex(targetTypeElements[0], targetTypeElements[1]).setSource(json, JSON);
        if(isNotBlank(key)) {
            indexRequest.setId(key);
        }
        indexRequest.get();
    }

    public ElasticSearchFunction clusterName(String clusterName) {
        this.clusterName = clusterName;
        return this;
    }

    public ElasticSearchFunction dateFields(List<String> dateFields) {
        this.dateFields = dateFields;
        return this;
    }

    public ElasticSearchFunction targetType(EventExpression<String> targetType) {
        this.targetType = targetType;
        return this;
    }

}