// src/main/java/.../api/AdminVmController.java
package org.caureq.caureqopsboard.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.ExecResponse;
import org.caureq.caureqopsboard.service.ActionService;
import org.caureq.caureqopsboard.service.AuditService;
import org.caureq.caureqopsboard.service.ProxmoxClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminVmController {

    private final ProxmoxClient proxmox;
    private final ActionService actions;
    private final AuditService audit;
    private final org.caureq.caureqopsboard.service.BulkActionService bulk;

    @PostMapping("/vm/{node}/{vmid}/start")
    public ResponseEntity<Map<String, String>> start(HttpServletRequest req,
                                                     @PathVariable String node, @PathVariable int vmid) {
        var upid = proxmox.startVm(node, vmid);
        audit.log(req, "admin-api", "start", node, vmid, "UPID=" + upid);
        return ResponseEntity.ok(Map.of("upid", upid));
    }

    @PostMapping("/vm/{node}/{vmid}/shutdown")
    public ResponseEntity<Map<String, String>> shutdown(HttpServletRequest req,
                                                        @PathVariable String node, @PathVariable int vmid) {
        var upid = proxmox.shutdownVm(node, vmid);
        audit.log(req, "admin-api", "shutdown", node, vmid, "UPID=" + upid);
        return ResponseEntity.ok(Map.of("upid", upid));
    }

    @PostMapping("/vm/{node}/{vmid}/stop")
    public ResponseEntity<Map<String, String>> stop(HttpServletRequest req,
                                                    @PathVariable String node, @PathVariable int vmid) {
        var upid = proxmox.stopVm(node, vmid);
        audit.log(req, "admin-api", "stop", node, vmid, "UPID=" + upid);
        return ResponseEntity.ok(Map.of("upid", upid));
    }

    @PostMapping("/vm/{node}/{vmid}/reset")
    public ResponseEntity<Map<String, String>> reset(HttpServletRequest req,
                                                     @PathVariable String node, @PathVariable int vmid) {
        var upid = proxmox.resetVm(node, vmid);
        audit.log(req, "admin-api", "reset", node, vmid, "UPID=" + upid);
        return ResponseEntity.ok(Map.of("upid", upid));
    }

    /** Requête d'exec: command est un tableau [prog, arg1, arg2, ...] */
    public record ExecRequest(List<String> command, String input, Integer timeoutSec) {}

    @PostMapping("/exec/{node}/{vmid}")
    public ResponseEntity<ExecResponse> exec(HttpServletRequest req,
                                             @PathVariable String node, @PathVariable int vmid,
                                             @RequestBody @jakarta.validation.Valid org.caureq.caureqopsboard.api.dto.ExecRequest body) {

        var timeout = java.time.Duration.ofSeconds(body.timeoutSec()==null ? 10 : body.timeoutSec());
        var program = body.command().get(0);
        var args = (body.command().size()>1) ? body.command().subList(1, body.command().size()) : java.util.List.of();

        var res = actions.execOnVm(node, vmid, program, (List<String>) args, body.input(), timeout);
        audit.log(req, "admin-api", "exec", node, vmid,
                "cmd=%s pid=%d exit=%d".formatted(program, res.pid(), res.exitCode()));

        return ResponseEntity.ok(new org.caureq.caureqopsboard.api.dto.ExecResponse(
                res.pid(), res.exitCode(), res.stdout(), res.stderr()
        ));
    }
    @GetMapping("/vm/{node}/{vmid}/agent/os")
    public com.fasterxml.jackson.databind.JsonNode osInfo(@PathVariable String node, @PathVariable int vmid) {
        return proxmox.agentGetOsInfo(node, vmid);
    }

    @GetMapping("/vm/{node}/{vmid}/agent/network")
    public com.fasterxml.jackson.databind.JsonNode network(@PathVariable String node, @PathVariable int vmid) {
        return proxmox.agentNetworkGetInterfaces(node, vmid);
    }

    // ---- Bulk operations ----
    public record BulkReq(List<String> hostnames, String tag) {}

    @PostMapping("/vm/bulk/start")
    public List<org.caureq.caureqopsboard.service.BulkActionService.Result> bulkStart(
            jakarta.servlet.http.HttpServletRequest req,
            @RequestBody BulkReq body) {
        return bulk.start(new org.caureq.caureqopsboard.service.BulkActionService.Request(body.hostnames, body.tag), req);
    }
    @PostMapping("/vm/bulk/shutdown")
    public List<org.caureq.caureqopsboard.service.BulkActionService.Result> bulkShutdown(
            jakarta.servlet.http.HttpServletRequest req,
            @RequestBody BulkReq body) {
        return bulk.shutdown(new org.caureq.caureqopsboard.service.BulkActionService.Request(body.hostnames, body.tag), req);
    }
    @PostMapping("/vm/bulk/stop")
    public List<org.caureq.caureqopsboard.service.BulkActionService.Result> bulkStop(
            jakarta.servlet.http.HttpServletRequest req,
            @RequestBody BulkReq body) {
        return bulk.stop(new org.caureq.caureqopsboard.service.BulkActionService.Request(body.hostnames, body.tag), req);
    }
    @PostMapping("/vm/bulk/reset")
    public List<org.caureq.caureqopsboard.service.BulkActionService.Result> bulkReset(
            jakarta.servlet.http.HttpServletRequest req,
            @RequestBody BulkReq body) {
        return bulk.reset(new org.caureq.caureqopsboard.service.BulkActionService.Request(body.hostnames, body.tag), req);
    }
}
