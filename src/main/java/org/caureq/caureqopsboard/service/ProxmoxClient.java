package org.caureq.caureqopsboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.caureq.caureqopsboard.config.ProxmoxProps;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProxmoxClient {

    private final ProxmoxProps props;
    private final WebClient proxmoxWebClient; // bean injecté
    private final ObjectMapper om = new ObjectMapper();

    @PostConstruct
    void checkToken() {
        var hdr = authHeader();
        var masked = hdr.replaceAll("=(.{4}).+$", "=$1********");
        log.info("[Proxmox] Auth header = {}", masked);
    }

    private String authHeader() {
        return "PVEAPIToken=%s=%s".formatted(props.tokenId(), props.tokenSecret());
    }

    /** Gestion d’erreur centralisée (supporte aussi 596 de PVE proxy) */
    private <T> Mono<T> handle(WebClient.ResponseSpec spec, Class<T> bodyType) {
        return spec
                .onStatus(s -> !s.is2xxSuccessful(),
                        r -> r.bodyToMono(String.class).defaultIfEmpty("")
                                .map(body -> new ProxmoxApiException(
                                        r.statusCode().value(),
                                        "Proxmox API error " + r.statusCode().value() + (body.isBlank() ? "" : " -> " + body),
                                        Map.of("raw", body)
                                ))
                )
                .bodyToMono(bodyType)
                .timeout(Duration.ofSeconds(Math.max(30, (int) props.defaultWait().toSeconds())))
                .retryWhen(
                        Retry.backoff(1, Duration.ofMillis(400)).jitter(0.4)
                                .filter(ex -> ex instanceof java.io.IOException || ex instanceof java.net.SocketTimeoutException)
                );
    }


    /* --------------------- Actions VM --------------------- */

    public String startVm(String node, int vmid) {
        var url = "%s/nodes/%s/qemu/%d/status/start".formatted(props.baseUrl(), node, vmid);
        var spec = proxmoxWebClient.post().uri(url)
                .header("Authorization", authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();
        return handle(spec, JsonNode.class).map(j -> j.path("data").asText()).block();
    }

    public String shutdownVm(String node, int vmid) {
        var url = "%s/nodes/%s/qemu/%d/status/shutdown".formatted(props.baseUrl(), node, vmid);
        var spec = proxmoxWebClient.post().uri(url)
                .header("Authorization", authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();
        return handle(spec, JsonNode.class).map(j -> j.path("data").asText()).block();
    }

    public String stopVm(String node, int vmid) {
        var url = "%s/nodes/%s/qemu/%d/status/stop".formatted(props.baseUrl(), node, vmid);
        return simplePostReturnUpid(url);
    }

    public String resetVm(String node, int vmid) {
        var url = "%s/nodes/%s/qemu/%d/status/reset".formatted(props.baseUrl(), node, vmid);
        return simplePostReturnUpid(url);
    }

    /* --------------------- QGA exec --------------------- */

    /**
     * Appelle /agent/exec
     * @param command tableau [prog, arg1, arg2, ...] – DOIT contenir au moins 1 élément
     * @param input   (optionnel) flux passé sur STDIN
     * @return pid du process côté guest
     */
// src/main/java/.../service/ProxmoxClient.java (extrait)
    public int guestExec(String node, int vmid, List<String> command, String input) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must contain at least the program");
        }
        String url = "%s/nodes/%s/qemu/%d/agent/exec".formatted(props.baseUrl(), node, vmid);

        // --- Schéma A : {"command": ["bash","-lc","whoami"], "input-data": "..."}
        var a = om.createObjectNode();
        a.set("command", om.valueToTree(command));
        if (input != null && !input.isBlank()) {
            a.put("input-data", input);
        }

        // --- Schéma B : {"command":"bash","args":["-lc","whoami"], "input-data": "..."}
        String program = command.get(0);
        List<String> args = (command.size() > 1) ? command.subList(1, command.size()) : List.of();
        var b = om.createObjectNode();
        b.put("command", program);
        b.set("args", om.valueToTree(args));
        if (input != null && !input.isBlank()) {
            b.put("input-data", input);
        }

        // Essai A puis fallback B si 400 "property is not defined in schema"
        return proxmoxWebClient.post()
                .uri(url)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(a)
                .exchangeToMono(resp -> {
                    if (resp.statusCode().is2xxSuccessful()) {
                        return resp.bodyToMono(JsonNode.class)
                                .map(j -> j.path("data").path("pid").asInt());
                    }
                    if (resp.statusCode().is4xxClientError()) {
                        return resp.bodyToMono(String.class).flatMap(body -> {
                            boolean schemaErr = body != null &&
                                    body.contains("property is not defined in schema");
                            if (schemaErr) {
                                // Retry avec schéma B
                                return proxmoxWebClient.post()
                                        .uri(url)
                                        .header("Authorization", authHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .bodyValue(b)
                                        .retrieve()
                                        .bodyToMono(JsonNode.class)
                                        .map(j -> j.path("data").path("pid").asInt());
                            }
                            return Mono.error(new ProxmoxApiException(
                                    resp.statusCode().value(),
                                    "Proxmox API error " + resp.statusCode().value() +
                                            (body.isBlank() ? "" : " -> " + body),
                                    Map.of("raw", body)));
                        });
                    }
                    // autres statuts (5xx, etc.)
                    return resp.bodyToMono(String.class).flatMap(body ->
                            Mono.error(new ProxmoxApiException(
                                    resp.statusCode().value(),
                                    "Proxmox API error " + resp.statusCode().value() +
                                            (body == null || body.isBlank() ? "" : " -> " + body),
                                    Map.of("raw", body)))
                    );
                })
                .timeout(Duration.ofSeconds(Math.max(30, (int) props.defaultWait().toSeconds())))
                .block();
    }



    public JsonNode guestExecStatus(String node, int vmid, int pid) {
        var url = "%s/nodes/%s/qemu/%d/agent/exec-status?pid=%d".formatted(props.baseUrl(), node, vmid, pid);
        var spec = proxmoxWebClient.get().uri(url)
                .header("Authorization", authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();
        return handle(spec, JsonNode.class).map(j -> j.path("data")).block();
    }

    /* --------------------- QGA diagnostics --------------------- */

    /** ping l’agent (utile pour du troubleshooting rapide) */
    public JsonNode agentPing(String node, int vmid) {
        var url = "%s/nodes/%s/qemu/%d/agent/ping".formatted(props.baseUrl(), node, vmid);
        var spec = proxmoxWebClient.get().uri(url)
                .header("Authorization", authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();
        return handle(spec, JsonNode.class).map(j -> j.path("data")).block();
    }

    public JsonNode agentGetOsInfo(String node, int vmid) {
        var url = "%s/nodes/%s/qemu/%d/agent/get-osinfo".formatted(props.baseUrl(), node, vmid);
        var spec = proxmoxWebClient.get().uri(url)
                .header("Authorization", authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();
        return handle(spec, JsonNode.class).map(j -> j.path("data")).block();
    }

    public JsonNode agentNetworkGetInterfaces(String node, int vmid) {
        var url = "%s/nodes/%s/qemu/%d/agent/network-get-interfaces".formatted(props.baseUrl(), node, vmid);
        var spec = proxmoxWebClient.get().uri(url)
                .header("Authorization", authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();
        return handle(spec, JsonNode.class).map(j -> j.path("data")).block();
    }

    /* --------------------- VM status --------------------- */

    public JsonNode vmCurrentStatus(String node, int vmid) {
        var url = "%s/nodes/%s/qemu/%d/status/current".formatted(props.baseUrl(), node, vmid);
        var spec = proxmoxWebClient.get().uri(url)
                .header("Authorization", authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();
        return handle(spec, JsonNode.class).map(j -> j.path("data")).block();
    }

    /* --------------------- utils --------------------- */

    public ProxmoxProps props() { return this.props; }

    public String simplePostReturnUpid(String url) {
        var spec = proxmoxWebClient.post().uri(url)
                .header("Authorization", authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();
        return handle(spec, JsonNode.class).map(j -> j.path("data").asText()).block();
    }

    /** Simple GET returning raw JSON (utility for discovery). */
    public JsonNode simpleGetJson(String url) {
        var spec = proxmoxWebClient.get().uri(url)
                .header("Authorization", authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();
        return handle(spec, JsonNode.class).block();
    }
}
