/**
 * يبني نسخة ثابتة (Static) من اللعبة داخل مجلد dist-static
 * تُستخدم لتغليف التطبيق كـ APK عبر Capacitor / GitHub Actions.
 *
 * لا يعتمد على تشغيل خادم SSR إطلاقاً: يستخدم النسخة المستقلة
 * public/tahaddi-standalone.html كصفحة index.html (تعمل دون إنترنت بالكامل).
 */
import { cp, mkdir, rm, copyFile, access, readdir } from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const OUT = path.join(root, "dist-static");
const PUBLIC = path.join(root, "public");
const STANDALONE = path.join(PUBLIC, "tahaddi-standalone.html");

const exists = async (p) => {
  try {
    await access(p);
    return true;
  } catch {
    return false;
  }
};

async function main() {
  if (!(await exists(STANDALONE))) {
    throw new Error("لم يُعثر على public/tahaddi-standalone.html");
  }

  await rm(OUT, { recursive: true, force: true });
  await mkdir(OUT, { recursive: true });
  await cp(PUBLIC, OUT, { recursive: true });

  // صفحة البداية للتطبيق = اللعبة المستقلة
  await copyFile(STANDALONE, path.join(OUT, "index.html"));

  // إزالة عامل الخدمة داخل التطبيق (غير مطلوب ويسبب تخزيناً مزدوجاً)
  await rm(path.join(OUT, "sw.js"), { force: true });

  const files = await readdir(OUT);
  if (!files.includes("index.html")) throw new Error("تعذّر توليد index.html الثابت");

  console.log("✅ dist-static جاهز للتغليف كـ APK —", files.length, "عنصراً");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
