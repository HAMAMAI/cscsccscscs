const originalMemoryUsage = process.memoryUsage.bind(process);

function safeMemoryUsage() {
  try {
    return originalMemoryUsage();
  } catch {
    return { rss: 0, heapTotal: 0, heapUsed: 0, external: 0, arrayBuffers: 0 };
  }
}

safeMemoryUsage.rss = () => {
  try {
    return originalMemoryUsage.rss();
  } catch {
    return 0;
  }
};

process.memoryUsage = safeMemoryUsage;

const os = require("node:os");
const originalNetworkInterfaces = os.networkInterfaces.bind(os);
os.networkInterfaces = () => {
  try {
    return originalNetworkInterfaces();
  } catch {
    return {
      lo: [{ address: "127.0.0.1", netmask: "255.0.0.0", family: "IPv4", mac: "00:00:00:00:00:00", internal: true, cidr: "127.0.0.1/8" }],
    };
  }
};
