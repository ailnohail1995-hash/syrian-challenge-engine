// Load this as an early script on an approved site to receive the Android port.
(() => {
  let port;
  let nextId = 0;
  const pending = new Map();

  window.addEventListener("message", (event) => {
    if (event.data !== "hybrid-lab:v1" || event.ports.length !== 1 || event.source) return;
    if (port) port.close();
    port = event.ports[0];
    port.onmessage = ({ data }) => {
      let reply;
      try { reply = JSON.parse(data); } catch { return; }
      const request = pending.get(reply.id);
      if (!request) return;
      pending.delete(reply.id);
      clearTimeout(request.timeout);
      if (reply.ok) request.resolve(reply);
      else request.reject(new Error(reply.error || "native_request_failed"));
    };
    window.dispatchEvent(new Event("hybrid-lab-ready"));
  });

  window.hybridLab = Object.freeze({
    get available() { return Boolean(port); },
    call(method, options = {}) {
      return new Promise((resolve, reject) => {
        if (!port) { reject(new Error("android_bridge_unavailable")); return; }
        const id = String(++nextId);
        const data = JSON.stringify({ ...options, id, method });
        const timeout = setTimeout(() => {
          pending.delete(id);
          reject(new Error("native_request_timeout"));
        }, 10000);
        pending.set(id, { resolve, reject, timeout });
        try { port.postMessage(data); }
        catch (error) { clearTimeout(timeout); pending.delete(id); reject(error); }
      });
    },
  });
})();
