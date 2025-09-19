package org.caureq.caureqopsboard.api.dto;

import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.service.ActionService;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/actions/vm")
@RequiredArgsConstructor
public class ActionController {
    private final ActionService actionService;

    // Protéger ces routes avec un filtre X-ADMIN-API-KEY
    @PostMapping("/{hostname}/start")
    public Map<String,String> start(@PathVariable String hostname){
        var upid = actionService.start(hostname);
        return Map.of("upid", upid);
    }

    @PostMapping("/{hostname}/shutdown")
    public Map<String,String> shutdown(@PathVariable String hostname){
        var upid = actionService.shutdown(hostname);
        return Map.of("upid", upid);
    }

    public record ExecReq(String command, List<String> args, String input, Integer timeoutSec) {}

    @PostMapping("/{hostname}/exec")
    public Map<String,Object> exec(@PathVariable String hostname, @RequestBody ExecReq req){
        var t = (req.timeoutSec()==null? Duration.ofSeconds(10) : Duration.ofSeconds(req.timeoutSec()));
        var r = actionService.exec(hostname, req.command(), req.args(), req.input(), t);
        return Map.of("exitCode", r.exitCode(), "stdout", r.stdout(), "stderr", r.stderr());
    }
}