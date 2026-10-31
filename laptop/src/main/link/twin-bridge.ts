/**
 * Bridge between Desktop sync link and local Python Attention Twin sidecar.
 * Forwards verified phone usage events, notification outcomes, and nudge telemetry
 * to the FastAPI sidecar at http://127.0.0.1:8765/ingest/batch.
 */
export async function forwardBatchToTwinSidecar(
  sidecarPort: number,
  sidecarToken: string,
  device: string,
  events: Array<{ ts: number; type: string; payload: any }>
): Promise<boolean> {
  if (!events || events.length === 0) return true;
  try {
    const res = await fetch(`http://127.0.0.1:${sidecarPort}/ingest/batch`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Twin-Token': sidecarToken,
        Authorization: `Bearer ${sidecarToken}`
      },
      body: JSON.stringify({ device, events })
    });
    return res.ok;
  } catch {
    // Sidecar may be starting or offline; events are preserved in local durable store
    return false;
  }
}
