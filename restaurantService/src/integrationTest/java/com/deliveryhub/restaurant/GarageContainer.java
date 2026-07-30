package com.deliveryhub.restaurant;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GarageContainer extends GenericContainer<GarageContainer> {

    public static final String ACCESS_KEY = "GK0123456789abcdef01234567";
    public static final String SECRET_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    public static final String BUCKET = "images";

    private static final int S3_PORT = 3900;
    private static final int ADMIN_PORT = 3903;
    private static final String ADMIN_TOKEN = "integration-test-admin-token";
    private static final String RPC_SECRET = "4f3c9a1e7b2d58606e1a9c34d7f0b2a85c61e93d4a7f208b1c5e6d9a3f4b7c10";
    private static final Pattern HEX_ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f]{64})\"");

    public GarageContainer() {
        super("dxflrs/garage:v2.3.0");
        withExposedPorts(S3_PORT, ADMIN_PORT);
        withEnv("GARAGE_RPC_SECRET", RPC_SECRET);
        withEnv("GARAGE_ADMIN_TOKEN", ADMIN_TOKEN);
        withCopyFileToContainer(MountableFile.forHostPath(Path.of("..", "garage", "garage.toml").toAbsolutePath()),
                "/etc/garage.toml");
        withTmpFs(Map.of("/var/lib/garage", "rw"));
        waitingFor(Wait.forListeningPorts(S3_PORT, ADMIN_PORT));
    }

    public String s3Endpoint() {
        return "http://" + getHost() + ":" + getMappedPort(S3_PORT);
    }

    @Override
    protected void containerIsStarted(com.github.dockerjava.api.command.InspectContainerResponse containerInfo) {
        try {
            bootstrap();
        } catch (IOException e) {
            throw new IllegalStateException("Garage bootstrap failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Garage bootstrap interrupted", e);
        }
    }

    private void bootstrap() throws IOException, InterruptedException {
        String status = admin("GET", "GetClusterStatus", null);
        String nodeId = firstId(status);
        admin("POST", "UpdateClusterLayout",
                "{\"roles\":[{\"id\":\"" + nodeId + "\",\"zone\":\"test\",\"capacity\":1073741824,\"tags\":[]}]}");
        admin("POST", "ApplyClusterLayout", "{\"version\":1}");
        admin("POST", "ImportKey", "{\"accessKeyId\":\"" + ACCESS_KEY + "\",\"secretAccessKey\":\"" + SECRET_KEY
                + "\",\"name\":\"integration-test\"}");
        String bucket = admin("POST", "CreateBucket", "{\"globalAlias\":\"" + BUCKET + "\"}");
        admin("POST", "AllowBucketKey", "{\"bucketId\":\"" + firstId(bucket) + "\",\"accessKeyId\":\"" + ACCESS_KEY
                + "\",\"permissions\":{\"read\":true,\"write\":true,\"owner\":false}}");
    }

    private String admin(String method, String action, String body) throws IOException, InterruptedException {
        URI uri = URI.create("http://" + getHost() + ":" + getMappedPort(ADMIN_PORT) + "/v2/" + action);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Garage " + action + " answered " + response.statusCode() + ": "
                    + response.body());
        }
        return response.body();
    }

    private static String firstId(String json) {
        Matcher matcher = HEX_ID.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No id in Garage response: " + json);
        }
        return matcher.group(1);
    }
}
