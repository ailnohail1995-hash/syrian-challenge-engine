/**
 * يبني نسخة ثابتة (Static) من اللعبة داخل مجلد dist-static
 * تُستخدم لتغليف التطبيق كـ APK عبر Capacitor / GitHub Actions.
 */
import { spawn } from "node:child_process";
import { cp, mkdir, rm, writeFile, access } from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const OUT = path.join(root, "dist-static");

const exists = async (p) => {
  try {
    await access(p);
    return true;
  } catch {
    return false;
  }
};

const run = (cmd, args) =>
  new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: "inherit", shell: process.platform === "win32" });
    p.on("exit", (c) => (c === 0 ? resolve() : reject(new Error(`${cmd} exited with ${c}`))));
  });

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  await run("npx", ["vite", "build"]);

  const publicDir = (await exists(path.join(root, ".output/public")))
    ? path.join(root, ".output/public")
    : path.join(root, "dist/client");

  if (!(await exists(publicDir))) throw new Error("لم يُعثر على مخرجات البناء");

  await rm(OUT, { recursive: true, force: true });
  await mkdir(OUT, { recursive: true });
  await cp(publicDir, OUT, { recursive: true });

  // تشغيل خادم الإخراج مؤقتاً لالتقاط صفحة HTML جاهزة للعمل دون إنترنت
  const serverEntry = path.join(root, ".output/server/index.mjs");
  let html = null;
  if (await exists(serverEntry)) {
    const srv = spawn("node", [serverEntry], {
      stdio: "ignore",
      env: { ...process.env, PORT: "3123", HOST: "127.0.0.1" },
    });
    try {
      for (let i = 0; i < 40; i++) {
        try {
          const res = await fetch("http://127.0.0.1:3123/");
          if (res.ok) {
            html = await res.text();
            break;
          }
        } catch {
          /* لم يبدأ بعد */
        }
        await wait(500);
      }
    } finally {
      srv.kill("SIGKILL");
    }
  }

  if (!html) throw new Error("تعذّر توليد index.html الثابت");
  await writeFile(path.join(OUT, "index.html"), html, "utf8");
  console.log("✅ dist-static جاهز للتغليف كـ APK");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
