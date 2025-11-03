# Missing VM Detection (The Why & How)

Why
- Proxmox VM lifecycle is dynamic. An asset mapped with `(node, vmid)` can be deleted or moved. We must detect this to keep the inventory clean and avoid broken admin actions.

Signals used
- Proxmox inventory listing
  - Prefer: `GET /cluster/resources?type=vm` (all nodes; QEMU+LXC)
  - Fallback: per node: `GET /nodes/{node}/qemu` and `.../lxc`
- Direct confirmation
  - `GET /nodes/{node}/qemu/{vmid}/status/current` — a 404 confirms “missing” even if listing is filtered by RBAC.

Algorithm (Discovery)
1) Build presence sets: `presentVmids` and `presentPairs` (node#vmid).
2) For each asset with a `vmid`:
   - If node is known: check `presentPairs`.
   - If node unknown: check `presentVmids` and validate name consistency if possible.
   - If not present, try `status/current` for confirmation.
3) Output: `missingAssets` list for the UI.

Runtime signal (Live)
- During scheduler refresh, if `status/current` throws 404, set `vmState="missing"` in the live cache for that host.

What to do in the UI
- Discovery ? Preview ? “Missing in Proxmox”.
- Clear mappings: removes node/vmid (asset remains in list).
- Archive missing: clears mapping + tags `retired` (defaults to hidden in the Assets list).

Tradeoffs
- Using cached live data keeps lists fast and resilient to Proxmox blips.
- Discovery’s explicit action helps avoid accidental data loss (we don’t auto-delete DB rows).
