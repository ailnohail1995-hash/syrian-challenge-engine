# بنك أسئلة "تحدّي الأحياء" — ملف مستقل جاهز للمشاركة

يحتوي هذا المجلد على بنك الأسئلة كاملاً (629 سؤالاً) بثلاث صيغ جاهزة للاستعمال في أي سكربت أو كود آخر:

| الملف | الاستخدام |
| --- | --- |
| `question-bank.json` | لأي لغة برمجة (Python, PHP, JS, Unity…) |
| `question-bank.js` | يوضع مباشرة في صفحة HTML: `<script src="question-bank.js"></script>` ثم `window.QUESTION_BANK` |
| `question-bank.csv` | لفتحه في Excel أو Google Sheets |

## بنية السؤال

```json
{
  "id": "DAM-E001",
  "category": "damascus",
  "category_ar": "دمشق",
  "level": "easy",
  "level_ar": "سهل",
  "question": "نص السؤال",
  "options": ["خيار 1", "خيار 2", "خيار 3", "خيار 4"],
  "answer": "الإجابة الصحيحة",
  "answer_index": 0,
  "explanation": "شرح مختصر"
}
```

- المجالات: `damascus` (دمشق) · `environment` (البيئة) · `development` (التنمية) · `initiative` (المبادرة).
- المستويات: `easy` · `medium` · `hard` · `legend` (تحدّي الثقافة).
- ملاحظة: رتّب الخيارات عشوائياً عند العرض حتى لا يبقى موقع الإجابة ثابتاً.

## مثال سريع (JavaScript)

```js
const bank = await fetch("/question-bank.json").then((r) => r.json());
const easyDamascus = bank.questions.filter((q) => q.category === "damascus" && q.level === "easy");
```

الترخيص: CC BY 4.0 — حرّ الاستخدام مع الإشارة للمصدر (محافظة دمشق — تحدّي الأحياء).
