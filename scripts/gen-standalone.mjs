/** يحقن بنك الأسئلة داخل القالب المستقل وينتج ملف HTML واحد يعمل دون إنترنت */
import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const tpl = await readFile(path.join(root, "scripts/standalone.template.html"), "utf8");
const bank = JSON.parse(await readFile(path.join(root, "public/question-bank.json"), "utf8"));

const json = JSON.stringify(bank).replace(/</g, "\\u003c").replace(/\u2028|\u2029/g, "");
const out = tpl.replace("/*__QUESTIONS__*/null", json);
if (out === tpl) throw new Error("لم يُعثر على موضع الحقن /*__QUESTIONS__*/null");

const targets = [
  path.join(root, "public/tahaddi-standalone.html"),
  "/mnt/documents/تحدي-الأحياء-اللعبة-الكاملة.html",
];
for (const t of targets) {
  await mkdir(path.dirname(t), { recursive: true });
  await writeFile(t, out, "utf8");
}
console.log(`✅ HTML مستقل: ${bank.questions.length} سؤال — ${(out.length / 1024 / 1024).toFixed(2)}MB`);
