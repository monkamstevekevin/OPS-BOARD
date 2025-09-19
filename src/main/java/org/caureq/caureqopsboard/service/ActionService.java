package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ActionService {
    private final ProxmoxClient proxmox;
    private final AssetRepo assetRepo;

    public String start(String hostname) {
        var meta = findVm(hostname);
        return proxmox.startVm(meta.node(), meta.vmid());
    }

    public String shutdown(String hostname) {
        var meta = findVm(hostname);
        return proxmox.shutdownVm(meta.node(), meta.vmid());
    }

    /** Exécution par node/vmid (utilisé par l'admin controller) */
    public ExecOutcome execOnVm(String node, int vmid,
                                String command, List<String> args, String input, Duration timeout) {

        // Construire le tableau attendu par Proxmox API
        List<String> fullCmd = new ArrayList<>();
        if (command != null && !command.isBlank()) {
            fullCmd.add(command);
        }
        if (args != null && !args.isEmpty()) {
            fullCmd.addAll(args);
        }

        // Appeler le client Proxmox (qui POST un JSON avec "command": fullCmd, "input-data": ...)
        int pid = proxmox.guestExec(node, vmid, fullCmd, input);

        long deadline = System.currentTimeMillis() + (timeout == null ? 10_000 : timeout.toMillis());
        while (System.currentTimeMillis() < deadline) {
            var st = proxmox.guestExecStatus(node, vmid, pid);
            if (st.path("exited").asInt(0) == 1) {
                return new ExecOutcome(
                        pid,
                        st.path("exitcode").asInt(1),
                        st.path("out-data").asText(""),
                        st.path("err-data").asText("")
                );
            }
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        }
        return new ExecOutcome(pid, 124, "", "timeout");
    }

    // Variante par hostname
    public ExecOutcome exec(String hostname, String command, List<String> args, String input, Duration timeout) {
        var meta = findVm(hostname);
        return execOnVm(meta.node(), meta.vmid(), command, args, input, timeout);
    }

    private VmMeta findVm(String hostname) {
        var a = assetRepo.findByHostnameIgnoreCase(hostname)
                .orElseThrow(() -> new IllegalArgumentException("asset not found: " + hostname));
        var m = Pattern.compile("^vm-(\\d+)-").matcher(a.getHostname());
        if (!m.find()) throw new IllegalArgumentException("cannot extract vmid from hostname: " + a.getHostname());
        int vmid = Integer.parseInt(m.group(1));
        String node = "Caureqlab"; // ⚠️ à améliorer : stocker le node en DB
        return new VmMeta(node, vmid);
    }

    private record VmMeta(String node, int vmid) {}
    public record ExecOutcome(int pid, int exitCode, String stdout, String stderr) {}
}

