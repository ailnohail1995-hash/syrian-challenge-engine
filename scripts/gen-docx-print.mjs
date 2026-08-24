/** نسخة Word جاهزة للطباعة (RTL) لبنك الأسئلة كاملاً */
import fs from "node:fs";
import {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  AlignmentType, HeadingLevel, BorderStyle, WidthType, ShadingType, Header, Footer, PageNumber,
} from "docx";

const bank = JSON.parse(fs.readFileSync("public/question-bank.json", "utf8"));
const CATS = [
  ["damascus", "دمشق"],
  ["environment", "البيئة"],
  ["development", "التنمية"],
  ["initiative", "المبادرة"],
];
const LEVS = [
  ["easy", "سهل"],
  ["medium", "وسط"],
  ["hard", "صعب"],
  ["legend", "تحدّي الثقافة"],
];
const F = "Arial";
const rtl = { bidirectional: true, alignment: AlignmentType.RIGHT };
const P = (text, o = {}) =>
  new Paragraph({
    ...rtl,
    spacing: { after: o.after ?? 60 },
    ...(o.para ?? {}),
    children: [new TextRun({ text, font: F, size: o.size ?? 22, bold: o.bold, color: o.color, rightToLeft: true })],
  });

const cellBorder = { style: BorderStyle.SINGLE, size: 1, color: "BBBBBB" };
const borders = { top: cellBorder, bottom: cellBorder, left: cellBorder, right: cellBorder };
const cell = (text, w, opts = {}) =>
  new TableCell({
    borders,
    width: { size: w, type: WidthType.DXA },
    shading: { fill: opts.fill ?? "FFFFFF", type: ShadingType.CLEAR },
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [P(text, { bold: opts.bold, size: 20 })],
  });

const children = [];
// غلاف
children.push(
  new Paragraph({ ...rtl, alignment: AlignmentType.CENTER, spacing: { before: 1200, after: 200 },
    children: [new TextRun({ text: "تحدّي الأحياء", font: F, size: 60, bold: true, color: "8A6A20", rightToLeft: true })] }),
  new Paragraph({ ...rtl, alignment: AlignmentType.CENTER, spacing: { after: 120 },
    children: [new TextRun({ text: "بنك الأسئلة الكامل — نسخة الطباعة", font: F, size: 32, rightToLeft: true })] }),
  new Paragraph({ ...rtl, alignment: AlignmentType.CENTER, spacing: { after: 800 },
    children: [new TextRun({ text: `محافظة دمشق · ${bank.questions.length} سؤالاً · المجالات: دمشق، البيئة، التنمية، المبادرة`, font: F, size: 22, color: "555555", rightToLeft: true })] }),
);

// جدول التوزّع
const total = bank.questions.length;
const rows = [new TableRow({ children: [cell("المجال", 2360, { bold: true, fill: "EFE6CC" }), ...LEVS.map(([, ar]) => cell(ar, 1750, { bold: true, fill: "EFE6CC" })) ] })];
for (const [c, car] of CATS) {
  rows.push(new TableRow({
    children: [cell(car, 2360, { bold: true, fill: "F7F3E8" }),
      ...LEVS.map(([l]) => cell(String(bank.questions.filter((q) => q.category === c && q.level === l).length), 1750))],
  }));
}
children.push(P("توزّع الأسئلة", { size: 28, bold: true, after: 160 }));
children.push(new Table({ width: { size: 9360, type: WidthType.DXA }, columnWidths: [2360, 1750, 1750, 1750, 1750], rows }));
children.push(P(`المجموع: ${total} سؤالاً`, { size: 22, after: 200, para: { spacing: { before: 160 } } }));

// الأسئلة
let n = 0;
for (const [c, car] of CATS) {
  children.push(new Paragraph({ ...rtl, pageBreakBefore: true, heading: HeadingLevel.HEADING_1, spacing: { after: 160 },
    children: [new TextRun({ text: `المجال: ${car}`, font: F, size: 34, bold: true, color: "8A6A20", rightToLeft: true })] }));
  for (const [l, lar] of LEVS) {
    const qs = bank.questions.filter((q) => q.category === c && q.level === l);
    if (!qs.length) continue;
    children.push(new Paragraph({ ...rtl, heading: HeadingLevel.HEADING_2, spacing: { before: 220, after: 120 },
      children: [new TextRun({ text: `المستوى: ${lar} (${qs.length} سؤالاً)`, font: F, size: 26, bold: true, color: "2F5D50", rightToLeft: true })] }));
    for (const q of qs) {
      n++;
      children.push(P(`${n}. ${q.question}`, { bold: true, size: 23, para: { spacing: { before: 140, after: 60 }, keepNext: true } }));
      const letters = ["أ", "ب", "ج", "د"];
      // خلط مواقع الخيارات عند الطباعة
      const opts = [...q.options].sort(() => Math.random() - 0.5);
      opts.forEach((o, i) => {
        const ok = o === q.answer;
        children.push(new Paragraph({ ...rtl, spacing: { after: 30 }, indent: { right: 340 },
          children: [new TextRun({ text: `${letters[i]}) ${o}${ok ? "  ✔" : ""}`, font: F, size: 21, bold: ok, color: ok ? "1E6B3A" : "222222", rightToLeft: true })] }));
      });
      children.push(P(`الإجابة: ${q.answer} — ${q.explanation}`, { size: 19, color: "555555", after: 40 }));
      children.push(P(`المعرّف: ${q.id.replace(/-/g, "\u2011")}`, { size: 16, color: "999999", after: 120 }));
    }
  }
}

const doc = new Document({
  styles: { default: { document: { run: { font: F, size: 22 } } } },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1134, right: 1134, bottom: 1134, left: 1134 } } },
    headers: { default: new Header({ children: [P("تحدّي الأحياء — بنك الأسئلة (نسخة الطباعة)", { size: 18, color: "888888" })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ ...rtl, alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: "صفحة ", font: F, size: 18, rightToLeft: true }), new TextRun({ children: [PageNumber.CURRENT], font: F, size: 18 })] })] }) },
    children,
  }],
});

const out = "/mnt/documents/تحدي-الأحياء-بنك-الأسئلة-للطباعة.docx";
fs.writeFileSync(out, await Packer.toBuffer(doc));
console.log("✅", out, n, "سؤالاً");
