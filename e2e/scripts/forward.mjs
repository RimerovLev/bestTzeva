// Внутри Docker-контейнера с браузером пробрасывает порты e2e-стека на хост, чтобы страницы
// открывались по тем же адресам localhost, что и без Docker (витрина ходит в API по localhost:18080,
// presigned-ссылки на фото ведут на MinIO localhost:9000).
import net from "node:net";

const HOST = process.env.E2E_HOST ?? "host.docker.internal";
const PORTS = (process.env.E2E_FORWARD_PORTS ?? "3100,5174,18080,9000").split(",").map(Number);

for (const port of PORTS) {
  net
    .createServer((client) => {
      const upstream = net.connect(port, HOST);
      client.pipe(upstream).pipe(client);
      const close = () => { client.destroy(); upstream.destroy(); };
      client.on("error", close);
      upstream.on("error", close);
    })
    .listen(port, "127.0.0.1", () => console.log(`forward localhost:${port} → ${HOST}:${port}`));
}
