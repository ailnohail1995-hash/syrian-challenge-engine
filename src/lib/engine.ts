import { QUESTIONS, type Cat, type Lev, type Question } from "@/data/questions";

export type Mode = "solo" | "duel";
export type Difficulty = "normal" | "hard" | "legend";
export type ItemKind = "normal" | "double" | "clash" | "legend";

export interface RoundItem {
  key: string;
  q: Question;
  opts: string[];
  ans: string;
  level: Lev;
  cat: Cat;
  points: number;
  time: number;
  /** null = الجميع (جولة مواجهة) */
  player: 0 | 1 | null;
  kind: ItemKind;
}

export const CATS: Record<Cat, { label: string; icon: string; color: string }> = {
  damascus: { label: "دمشق", icon: "🏛️", color: "var(--cat-damascus)" },
  environment: { label: "البيئة", icon: "🌿", color: "var(--cat-environment)" },
  development: { label: "التنمية", icon: "📈", color: "var(--cat-development)" },
  initiative: { label: "المبادرة", icon: "🚀", color: "var(--cat-initiative)" },
};

export const LEVELS: Record<Lev, { label: string; points: number; time: number; tone: string }> = {
  easy: { label: "اكتشاف", points: 100, time: 20, tone: "var(--lev-easy)" },
  medium: { label: "معرفة", points: 200, time: 16, tone: "var(--lev-medium)" },
  hard: { label: "تحليل", points: 350, time: 13, tone: "var(--lev-hard)" },
  legend: { label: "تحدّي الثقافة", points: 700, time: 11, tone: "var(--lev-legend)" },
};

const SEEN_KEY = "tahaddi_seen_v1";

function loadSeen(): Set<string> {
  if (typeof window === "undefined") return new Set();
  try {
    const raw = window.localStorage.getItem(SEEN_KEY);
    return new Set<string>(raw ? (JSON.parse(raw) as string[]) : []);
  } catch {
    return new Set();
  }
}

function saveSeen(seen: Set<string>) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(SEEN_KEY, JSON.stringify([...seen]));
  } catch {
    /* التخزين غير متاح — لا مشكلة */
  }
}

export function resetSeen() {
  if (typeof window !== "undefined") window.localStorage.removeItem(SEEN_KEY);
}

export function shuffle<T>(arr: readonly T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    const t = a[i] as T;
    a[i] = a[j] as T;
    a[j] = t;
  }
  return a;
}

const POOL: Record<string, Question[]> = {};
for (const q of QUESTIONS) {
  const k = `${q.c}|${q.l}`;
  (POOL[k] ??= []).push(q);
}

export const BANK_SIZE = QUESTIONS.length;

/** خطة الجولة: مستوى + مجال لكل سؤال، تصاعدية ومتنوّعة */
function ladder(difficulty: Difficulty): Lev[] {
  if (difficulty === "legend")
    return ["medium", "hard", "hard", "legend", "hard", "legend", "hard", "legend", "legend", "legend"];
  if (difficulty === "hard")
    return ["easy", "medium", "medium", "hard", "medium", "hard", "hard", "hard", "legend", "legend"];
  return ["easy", "easy", "medium", "easy", "medium", "medium", "hard", "hard", "hard", "legend"];
}

interface Picker {
  pick: (lev: Lev, cat?: Cat) => Question;
}

function makePicker(): Picker {
  const seen = loadSeen();
  const usedNow = new Set<string>();
  return {
    pick(lev: Lev, cat?: Cat): Question {
      const cats: Cat[] = cat ? [cat] : shuffle(Object.keys(CATS) as Cat[]);
      const gather = (allowSeen: boolean) => {
        const out: Question[] = [];
        for (const c of cats) {
          for (const q of POOL[`${c}|${lev}`] ?? []) {
            if (usedNow.has(q.id)) continue;
            if (!allowSeen && seen.has(q.id)) continue;
            out.push(q);
          }
        }
        return out;
      };
      let pool = gather(false);
      if (pool.length === 0) pool = gather(true); // نفدت الأسئلة الجديدة لهذا المزيج
      if (pool.length === 0) {
        // احتياط أقصى: أي سؤال من نفس المستوى
        pool = QUESTIONS.filter((q) => q.l === lev && !usedNow.has(q.id));
      }
      const q = pool[Math.floor(Math.random() * pool.length)] ?? (QUESTIONS[0] as Question);
      usedNow.add(q.id);
      seen.add(q.id);
      saveSeen(seen);
      return q;
    },
  };
}

function makeItem(
  q: Question,
  player: 0 | 1 | null,
  kind: ItemKind,
  idx: number,
): RoundItem {
  const meta = LEVELS[q.l];
  const opts = shuffle(q.o); // مكان الإجابة الصحيحة يتغيّر في كل مرة
  return {
    key: `${q.id}-${idx}-${Math.random().toString(36).slice(2, 7)}`,
    q,
    opts,
    ans: q.a,
    level: q.l,
    cat: q.c,
    points: Math.round(meta.points * (kind === "clash" ? 1.5 : 1)),
    time: kind === "clash" ? 10 : meta.time,
    player,
    kind,
  };
}

/** يبني جولة كاملة: مستويات متصاعدة، مجالات متناوبة، بلا تكرار، وخيارات مخلوطة */
export function buildRound(mode: Mode, difficulty: Difficulty): RoundItem[] {
  const picker = makePicker();
  const plan = ladder(difficulty);
  const catCycle = shuffle(Object.keys(CATS) as Cat[]);
  const items: RoundItem[] = [];

  if (mode === "solo") {
    plan.forEach((lev, i) => {
      const cat = catCycle[i % catCycle.length] as Cat;
      const kind: ItemKind = lev === "legend" && i === plan.length - 1 ? "legend" : "normal";
      items.push(makeItem(picker.pick(lev, cat), 0, kind, i));
    });
    // سؤال المفاجأة قبل الأخير
    const surpriseAt = plan.length - 2;
    const surprise = makeItem(picker.pick("hard"), 0, "double", 99);
    items.splice(surpriseAt, 0, surprise);
    return items;
  }

  // مواجهة ثنائية: لكل لاعب سؤاله ووقته، بنفس درجة الصعوبة
  const pairs = plan.slice(0, 5);
  pairs.forEach((lev, i) => {
    const c0 = catCycle[(i * 2) % catCycle.length] as Cat;
    const c1 = catCycle[(i * 2 + 1) % catCycle.length] as Cat;
    items.push(makeItem(picker.pick(lev, c0), 0, "normal", i * 2));
    items.push(makeItem(picker.pick(lev, c1), 1, "normal", i * 2 + 1));
  });
  items.push(makeItem(picker.pick("hard"), null, "clash", 50)); // ⚡ جولة المواجهة
  items.push(makeItem(picker.pick("legend"), 0, "legend", 60));
  items.push(makeItem(picker.pick("legend"), 1, "legend", 61));
  return items;
}

/** النقاط مع مكافأة السرعة الخفيفة ومضاعف السلسلة */
export function scoreFor(item: RoundItem, remaining: number, streak: number) {
  const ratio = Math.max(0, Math.min(1, remaining / item.time));
  const speed = 1 + ratio * 0.25;
  const streakMul = streak >= 5 ? 1.5 : streak >= 3 ? 1.25 : 1;
  const doubleMul = item.kind === "double" ? 2 : 1;
  return Math.round(item.points * speed * streakMul * doubleMul);
}

export interface Identity {
  title: string;
  icon: string;
  line: string;
}

export function identityFor(
  score: number,
  correct: number,
  total: number,
  bestCat: Cat | null,
  reachedLegend: boolean,
): Identity {
  const acc = total ? correct / total : 0;
  if (acc >= 0.9 && reachedLegend)
    return { title: "موسوعة الشام", icon: "👑", line: "معرفة نادرة… أنت مرجع لا لاعب." };
  if (acc >= 0.75) {
    if (bestCat === "damascus")
      return { title: "دمشقيّ المعرفة", icon: "🏛️", line: "تعرف المدينة من تفاصيلها لا من صورها." };
    if (bestCat === "environment")
      return { title: "واعي البيئة", icon: "♻️", line: "تفهم أن البيئة قرار يومي لا شعار." };
    if (bestCat === "development")
      return { title: "صديق التنمية", icon: "🌱", line: "تقيس الأثر لا النشاط." };
    return { title: "محلّل المبادرات", icon: "🧠", line: "تبدأ من المشكلة لا من الحماس." };
  }
  if (acc >= 0.5)
    return { title: "ابن الحارة", icon: "🏘️", line: "أساس متين… ينقصك التعمّق قليلاً." };
  if (score > 0)
    return { title: "مستكشف", icon: "🧭", line: "البداية دائماً من سؤال لم نعرف جوابه." };
  return { title: "زائر جديد", icon: "✨", line: "جرّب مرة أخرى — الأسئلة تتغيّر كلياً." };
}
