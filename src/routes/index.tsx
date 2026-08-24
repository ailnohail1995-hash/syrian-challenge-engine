import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { CATS, LEVELS, BANK_SIZE, buildRound, identityFor, resetSeen, scoreFor, type Difficulty, type Mode, type RoundItem } from "@/lib/engine";
import type { Cat } from "@/data/questions";
import { CORRECT_MSGS, LEGEND_INTRO, STREAK_MSGS, TIMEOUT_MSGS, WRONG_MSGS, pickMsg } from "@/data/messages";
import { sfx } from "@/lib/sound";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "تحدّي الأحياء — لعبة دمشق المعرفية" },
      {
        name: "description",
        content:
          "لعبة تفاعلية فاخرة عن دمشق والبيئة والتنمية والمبادرة: أربعة مستويات، مواجهة ثنائية، مؤقّت حيّ ونتائج بهوية معرفية.",
      },
      { property: "og:title", content: "تحدّي الأحياء — لعبة دمشق المعرفية" },
      {
        property: "og:description",
        content: "أربعة مجالات، أربعة مستويات، ومواجهة ثنائية… هل تستحقّ لقب موسوعة الشام؟",
      },
    ],
  }),
  component: Index,
});

type Phase = "intro" | "mode" | "setup" | "countdown" | "play" | "results";

interface PlayerState {
  name: string;
  score: number;
  correct: number;
  answered: number;
  streak: number;
  best: number;
  perCat: Record<Cat, number>;
  legend: boolean;
}

const emptyCat = (): Record<Cat, number> => ({ damascus: 0, environment: 0, development: 0, initiative: 0 });
const newPlayer = (name: string): PlayerState => ({
  name,
  score: 0,
  correct: 0,
  answered: 0,
  streak: 0,
  best: 0,
  perCat: emptyCat(),
  legend: false,
});

const DIFFS: { id: Difficulty; label: string; hint: string; icon: string }[] = [
  { id: "normal", label: "رحلة المعرفة", hint: "تدرّج هادئ من الاكتشاف إلى التحليل", icon: "🕌" },
  { id: "hard", label: "طريق المحترف", hint: "أسئلة تحليل أكثر ولحظات ضغط", icon: "⚔️" },
  { id: "legend", label: "تحدّي الثقافة", hint: "نخبة فقط — أسئلة نادرة ووقت قصير", icon: "👑" },
];

function Index() {
  const [phase, setPhase] = useState<Phase>("intro");
  const [mode, setMode] = useState<Mode>("solo");
  const [difficulty, setDifficulty] = useState<Difficulty>("normal");
  const [names, setNames] = useState<[string, string]>(["اللاعب الأول", "اللاعب الثاني"]);
  const [players, setPlayers] = useState<[PlayerState, PlayerState]>([newPlayer("اللاعب الأول"), newPlayer("اللاعب الثاني")]);
  const [items, setItems] = useState<RoundItem[]>([]);
  const [idx, setIdx] = useState(0);
  const [remaining, setRemaining] = useState(20);
  const [picked, setPicked] = useState<string | null>(null);
  const [locked, setLocked] = useState(false);
  const [feedback, setFeedback] = useState<{ ok: boolean; msg: string; gained: number; timeout?: boolean } | null>(null);
  const [buzzer, setBuzzer] = useState<0 | 1 | null>(null);
  const [count, setCount] = useState(3);
  const [muted, setMuted] = useState(false);
  const [toast, setToast] = useState<string | null>(null);

  const item = items[idx];
  const activePlayer: 0 | 1 = item ? (item.player ?? buzzer ?? 0) : 0;

  /* ---------------- الصوت ---------------- */
  const toggleMute = () => {
    const m = !muted;
    setMuted(m);
    sfx.setMuted(m);
  };

  /* ---------------- المؤقّت ---------------- */
  useEffect(() => {
    if (phase !== "play" || !item || locked) return;
    if (item.kind === "clash" && buzzer === null) return;
    setRemaining(item.time);
    let left = item.time;
    const t = window.setInterval(() => {
      left -= 1;
      setRemaining(left);
      if (left <= 5 && left > 0) sfx.tick(left <= 3);
      if (left <= 0) {
        window.clearInterval(t);
        handleTimeout();
      }
    }, 1000);
    return () => window.clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase, idx, locked, buzzer]);

  /* ---------------- العدّ التنازلي ---------------- */
  useEffect(() => {
    if (phase !== "countdown") return;
    setCount(3);
    let n = 3;
    sfx.countdown(3);
    const t = window.setInterval(() => {
      n -= 1;
      setCount(n);
      sfx.countdown(n);
      if (n <= 0) {
        window.clearInterval(t);
        window.setTimeout(() => setPhase("play"), 650);
      }
    }, 850);
    return () => window.clearInterval(t);
  }, [phase]);

  /* ---------------- مقدّمة تحدّي الثقافة ---------------- */
  useEffect(() => {
    if (phase !== "play" || !item) return;
    if (item.level === "legend") {
      sfx.legend();
      setToast(pickMsg(LEGEND_INTRO, "legend"));
      const t = window.setTimeout(() => setToast(null), 2600);
      return () => window.clearTimeout(t);
    }
    sfx.whoosh();
    return;
  }, [phase, idx]); // eslint-disable-line react-hooks/exhaustive-deps

  const start = useCallback(() => {
    sfx.unlock();
    sfx.startMusic();
    const built = buildRound(mode, difficulty);
    setItems(built);
    setIdx(0);
    setPicked(null);
    setLocked(false);
    setFeedback(null);
    setBuzzer(null);
    setPlayers([newPlayer(names[0] || "اللاعب الأول"), newPlayer(names[1] || "اللاعب الثاني")]);
    setPhase("countdown");
  }, [mode, difficulty, names]);

  const commit = (ok: boolean, gained: number, timeout: boolean) => {
    const p = activePlayer;
    setPlayers((prev) => {
      const next: [PlayerState, PlayerState] = [{ ...prev[0] }, { ...prev[1] }];
      const cur = next[p];
      cur.answered += 1;
      if (ok) {
        cur.correct += 1;
        cur.score += gained;
        cur.streak += 1;
        cur.best = Math.max(cur.best, cur.streak);
        if (item) cur.perCat[item.cat] += 1;
        if (item?.level === "legend") cur.legend = true;
      } else {
        cur.streak = 0;
      }
      return next;
    });
    setFeedback({
      ok,
      gained,
      timeout,
      msg: timeout ? pickMsg(TIMEOUT_MSGS, "to") : ok ? pickMsg(CORRECT_MSGS, "ok") : pickMsg(WRONG_MSGS, "no"),
    });
  };

  function handleTimeout() {
    if (locked) return;
    setLocked(true);
    sfx.timeout();
    commit(false, 0, true);
  }

  const answer = (opt: string) => {
    if (locked || !item) return;
    setLocked(true);
    setPicked(opt);
    const ok = opt === item.ans;
    const streak = players[activePlayer].streak;
    const gained = ok ? scoreFor(item, remaining, streak) : 0;
    if (ok) {
      sfx.correct();
      if (streak + 1 >= 3) window.setTimeout(() => sfx.streak(streak + 1), 400);
    } else sfx.wrong();
    commit(ok, gained, false);
  };

  const next = () => {
    sfx.click();
    setPicked(null);
    setFeedback(null);
    setLocked(false);
    setBuzzer(null);
    if (idx + 1 >= items.length) {
      sfx.stopMusic();
      sfx.victory();
      setPhase("results");
    } else {
      setIdx(idx + 1);
    }
  };

  const backHome = () => {
    sfx.stopMusic();
    sfx.click();
    setPhase("intro");
  };

  return (
    <main className="dmsc-bg dmsc-pattern relative h-[100dvh] w-full overflow-hidden">
      <button
        onClick={toggleMute}
        aria-label={muted ? "تشغيل الصوت" : "كتم الصوت"}
        className="glass fixed top-4 left-4 z-50 rounded-full px-3 py-2 text-lg transition hover:scale-110"
      >
        {muted ? "🔇" : "🔊"}
      </button>

      {toast && (
        <div className="anim-pop glass glow-gold fixed inset-x-4 top-20 z-50 mx-auto max-w-md rounded-2xl px-5 py-3 text-center text-sm text-goldsoft">
          {toast}
        </div>
      )}

      <div className="relative z-10 mx-auto flex h-full min-h-0 w-full max-w-3xl flex-col overflow-hidden px-4 py-3">

        {phase === "intro" && <Intro onStart={() => { sfx.unlock(); sfx.select(); setPhase("mode"); }} />}

        {phase === "mode" && (
          <ModeScreen
            mode={mode}
            onMode={(m) => { sfx.select(); setMode(m); }}
            onNext={() => { sfx.click(); setPhase("setup"); }}
            onBack={backHome}
          />
        )}

        {phase === "setup" && (
          <SetupScreen
            mode={mode}
            difficulty={difficulty}
            names={names}
            onDiff={(d) => { sfx.select(); setDifficulty(d); }}
            onName={(i, v) => setNames((n) => (i === 0 ? [v, n[1]] : [n[0], v]))}
            onStart={start}
            onBack={() => { sfx.click(); setPhase("mode"); }}
          />
        )}

        {phase === "countdown" && <Countdown n={count} />}

        {phase === "play" && item && (
          <PlayScreen
            item={item}
            index={idx}
            total={items.length}
            remaining={remaining}
            picked={picked}
            locked={locked}
            feedback={feedback}
            mode={mode}
            players={players}
            activePlayer={activePlayer}
            buzzer={buzzer}
            onBuzz={(p) => { sfx.clash(); setBuzzer(p); }}
            onAnswer={answer}
            onNext={next}
          />
        )}

        {phase === "results" && (
          <Results
            mode={mode}
            players={players}
            total={items.length}
            onReplay={start}
            onHome={backHome}
            onReset={() => { resetSeen(); sfx.select(); setToast("تمّت تهيئة بنك الأسئلة من جديد ✨"); window.setTimeout(() => setToast(null), 2200); }}
          />
        )}
      </div>
    </main>
  );
}

/* ============================= الشاشات ============================= */

function Intro({ onStart }: { onStart: () => void }) {
  return (
    <section className="anim-in flex min-h-0 flex-1 flex-col items-center justify-center overflow-hidden text-center">
      <div className="anim-ring mb-3 flex h-16 w-16 shrink-0 items-center justify-center rounded-full border border-gold/40 text-3xl sm:mb-5 sm:h-24 sm:w-24 sm:text-5xl">
        🏛️
      </div>
      <p className="mb-2 text-xs tracking-[0.4em] text-goldsoft/70">محافظة دمشق تقدّم</p>
      <h1 className="gold-text font-display text-4xl leading-tight sm:text-6xl lg:text-7xl">تحدّي الأحياء</h1>
      <p className="mt-3 hidden max-w-md text-sm leading-relaxed text-jasmine/75 sm:block sm:text-base">
        رحلة معرفية في دمشق والبيئة والتنمية والمبادرة… أسئلة لا تتكرّر، إجابات تتبدّل أماكنها، ووقتٌ لا يرحم.
      </p>

      <div className="mt-5 grid w-full max-w-sm shrink-0 grid-cols-4 gap-2">
        {(Object.keys(CATS) as Cat[]).map((c) => (
          <div key={c} className="glass rounded-2xl px-2 py-3 text-center">
            <div className="text-xl">{CATS[c].icon}</div>
            <div className="mt-1 text-[11px] text-jasmine/70">{CATS[c].label}</div>
          </div>
        ))}
      </div>

      <button
        onClick={onStart}
        className="shimmer glow-gold relative mt-6 shrink-0 overflow-hidden rounded-full bg-gradient-to-l from-gold to-goldsoft px-10 py-3 text-base font-extrabold text-night transition hover:scale-105 active:scale-95 sm:mt-8 sm:px-12 sm:py-4 sm:text-lg"
      >
        ابدأ التحدّي
      </button>
      <p className="mt-3 text-[11px] text-jasmine/45">{BANK_SIZE} سؤالاً في البنك · أربعة مستويات</p>
    </section>
  );
}

function ModeScreen({
  mode,
  onMode,
  onNext,
  onBack,
}: {
  mode: Mode;
  onMode: (m: Mode) => void;
  onNext: () => void;
  onBack: () => void;
}) {
  const card = (m: Mode, icon: string, title: string, desc: string) => (
    <button
      key={m}
      onClick={() => onMode(m)}
      className={`glass relative overflow-hidden rounded-3xl p-6 text-right transition hover:scale-[1.02] ${
        mode === m ? "glow-gold border-gold/70" : "opacity-80"
      }`}
    >
      <div className="mb-3 text-4xl">{icon}</div>
      <h3 className="font-display text-2xl text-goldsoft">{title}</h3>
      <p className="mt-2 text-sm leading-relaxed text-jasmine/70">{desc}</p>
      {mode === m && <span className="absolute left-4 top-4 text-gold">✔</span>}
    </button>
  );

  return (
    <section className="anim-in flex flex-1 flex-col justify-center">
      <h2 className="mb-1 text-center font-display text-4xl gold-text">اختر نمط اللعب</h2>
      <p className="mb-7 text-center text-sm text-jasmine/60">تلعب وحدك… أم تُثبت تفوّقك أمام خصم؟</p>
      <div className="grid gap-4 sm:grid-cols-2">
        {card("solo", "🧭", "فردي", "سلّم تصاعدي من 11 سؤالاً مع سؤال مفاجأة مضاعف وختام من تحدّي الثقافة.")}
        {card("duel", "⚔️", "ثنائي", "لاعبان بالتناوب، جولة مواجهة ⚡ بنقاط مضاعفة، وسؤالا نخبة في الختام.")}
      </div>
      <div className="mt-8 flex justify-between gap-3">
        <button onClick={onBack} className="glass rounded-full px-6 py-3 text-sm text-jasmine/80">رجوع</button>
        <button onClick={onNext} className="glow-gold rounded-full bg-gradient-to-l from-gold to-goldsoft px-10 py-3 font-bold text-night transition hover:scale-105">
          التالي
        </button>
      </div>
    </section>
  );
}

function SetupScreen({
  mode,
  difficulty,
  names,
  onDiff,
  onName,
  onStart,
  onBack,
}: {
  mode: Mode;
  difficulty: Difficulty;
  names: [string, string];
  onDiff: (d: Difficulty) => void;
  onName: (i: 0 | 1, v: string) => void;
  onStart: () => void;
  onBack: () => void;
}) {
  return (
    <section className="anim-in flex flex-1 flex-col justify-center">
      <h2 className="mb-1 text-center font-display text-4xl gold-text">درجة التحدّي</h2>
      <p className="mb-6 text-center text-sm text-jasmine/60">كل مستوى يغيّر الأسئلة والوقت والنقاط.</p>

      <div className="grid gap-3">
        {DIFFS.map((d) => (
          <button
            key={d.id}
            onClick={() => onDiff(d.id)}
            className={`glass flex items-center gap-4 rounded-2xl p-4 text-right transition hover:scale-[1.01] ${
              difficulty === d.id ? "glow-gold border-gold/70" : "opacity-80"
            }`}
          >
            <span className="text-3xl">{d.icon}</span>
            <span className="flex-1">
              <span className="block font-display text-xl text-goldsoft">{d.label}</span>
              <span className="block text-xs text-jasmine/65">{d.hint}</span>
            </span>
            {difficulty === d.id && <span className="text-gold">✔</span>}
          </button>
        ))}
      </div>

      <div className="mt-6 grid gap-3 sm:grid-cols-2">
        <label className="glass rounded-2xl px-4 py-3">
          <span className="block text-[11px] text-jasmine/55">اسم اللاعب الأول</span>
          <input
            value={names[0]}
            onChange={(e) => onName(0, e.target.value)}
            className="w-full bg-transparent text-goldsoft outline-none"
            maxLength={18}
          />
        </label>
        {mode === "duel" && (
          <label className="glass rounded-2xl px-4 py-3">
            <span className="block text-[11px] text-jasmine/55">اسم اللاعب الثاني</span>
            <input
              value={names[1]}
              onChange={(e) => onName(1, e.target.value)}
              className="w-full bg-transparent text-goldsoft outline-none"
              maxLength={18}
            />
          </label>
        )}
      </div>

      <div className="mt-8 flex justify-between gap-3">
        <button onClick={onBack} className="glass rounded-full px-6 py-3 text-sm text-jasmine/80">رجوع</button>
        <button onClick={onStart} className="shimmer glow-gold relative overflow-hidden rounded-full bg-gradient-to-l from-gold to-goldsoft px-10 py-3 font-extrabold text-night transition hover:scale-105">
          انطلق 🚀
        </button>
      </div>
    </section>
  );
}

function Countdown({ n }: { n: number }) {
  return (
    <section className="flex flex-1 items-center justify-center">
      <div key={n} className="anim-pop font-display text-[7rem] leading-none gold-text">
        {n > 0 ? n : "هيّا!"}
      </div>
    </section>
  );
}

function PlayScreen({
  item,
  index,
  total,
  remaining,
  picked,
  locked,
  feedback,
  mode,
  players,
  activePlayer,
  buzzer,
  onBuzz,
  onAnswer,
  onNext,
}: {
  item: RoundItem;
  index: number;
  total: number;
  remaining: number;
  picked: string | null;
  locked: boolean;
  feedback: { ok: boolean; msg: string; gained: number; timeout?: boolean } | null;
  mode: Mode;
  players: [PlayerState, PlayerState];
  activePlayer: 0 | 1;
  buzzer: 0 | 1 | null;
  onBuzz: (p: 0 | 1) => void;
  onAnswer: (o: string) => void;
  onNext: () => void;
}) {
  const cat = CATS[item.cat];
  const lev = LEVELS[item.level];
  const pct = Math.max(0, Math.min(1, remaining / item.time));
  const urgent = remaining <= 5 && !locked;
  const needsBuzz = item.kind === "clash" && buzzer === null;
  const streak = players[activePlayer].streak;

  return (
    <section className="flex h-full min-h-0 flex-1 flex-col">
      {/* الشريط العلوي */}
      <header className="mb-3 flex shrink-0 items-center justify-between gap-2 text-xs">
        <span className="glass rounded-full px-3 py-1.5">سؤال {index + 1}/{total}</span>
        <span className="glass rounded-full px-3 py-1.5" style={{ color: cat.color }}>
          {cat.icon} {cat.label}
        </span>
        <span className="glass rounded-full px-3 py-1.5" style={{ color: lev.tone }}>{lev.label}</span>
      </header>

      {/* النتائج الحيّة */}
      <div className={`mb-3 grid shrink-0 gap-2 ${mode === "duel" ? "grid-cols-2" : "grid-cols-1"}`}>
        {(mode === "duel" ? [0, 1] : [0]).map((i) => {
          const p = players[i as 0 | 1];
          const on = i === activePlayer && !needsBuzz;
          return (
            <div key={i} className={`glass rounded-2xl px-4 py-1.5 ${on ? "glow-gold" : "opacity-70"}`}>
              <div className="flex items-center justify-between">
                <span className="truncate text-xs text-jasmine/70">{p.name}</span>
                <span className="font-display text-lg text-goldsoft">{p.score}</span>
              </div>
              {p.streak >= 2 && <div className="text-[10px] text-gold">🔥 سلسلة ×{p.streak}</div>}
            </div>
          );
        })}
      </div>

      {/* المؤقّت */}
      <div className="mb-3 shrink-0">
        <div className="mb-1 flex items-center justify-between text-[11px] text-jasmine/60">
          <span>{item.kind === "double" ? "⭐ سؤال مضاعف ×2" : item.kind === "clash" ? "⚡ جولة المواجهة" : "الوقت المتبقّي"}</span>
          <span className={urgent ? "font-bold text-destructive" : ""}>{Math.max(0, remaining)} ثا</span>
        </div>
        <div className="h-2.5 overflow-hidden rounded-full bg-white/10">
          <div
            className={`h-full rounded-full transition-[width] duration-1000 ease-linear ${urgent ? "anim-shake" : ""}`}
            style={{
              width: `${pct * 100}%`,
              background: urgent
                ? "linear-gradient(90deg, oklch(0.7 0.2 25), oklch(0.75 0.19 40))"
                : `linear-gradient(90deg, ${lev.tone}, var(--dmsc-gold))`,
            }}
          />
        </div>
      </div>

      {/* جولة المواجهة: من يضغط أولاً */}
      {needsBuzz ? (
        <div className="anim-in glass flex min-h-0 flex-1 flex-col items-center justify-center gap-4 rounded-3xl p-5 text-center">
          <div className="text-4xl">⚡</div>
          <h3 className="font-display text-2xl gold-text">جولة المواجهة</h3>
          <p className="text-xs text-jasmine/70">من يعرف الجواب؟ اضغط زرّك أولاً — النقاط ×1.5 والوقت 10 ثوانٍ فقط.</p>
          <div className="grid w-full grid-cols-2 gap-3">
            {[0, 1].map((i) => (
              <button
                key={i}
                onClick={() => onBuzz(i as 0 | 1)}
                className="anim-ring rounded-2xl border border-gold/40 bg-white/5 px-4 py-6 font-display text-lg text-goldsoft transition hover:scale-105 active:scale-95"
              >
                {players[i as 0 | 1].name}
              </button>
            ))}
          </div>
        </div>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col gap-3">
          <div key={item.key} className="anim-in glass shrink-0 rounded-3xl px-4 py-3">
            {mode === "duel" && (
              <p className="mb-1 text-[11px] text-gold">دور: {players[activePlayer].name}</p>
            )}
            <h2 className="text-base font-bold leading-relaxed text-jasmine sm:text-xl">{item.q.q}</h2>
          </div>

          <div className="grid min-h-0 flex-1 grid-rows-4 gap-2">
            {item.opts.map((o, i) => {
              const isAns = o === item.ans;
              const isPicked = o === picked;
              const state = !locked ? "idle" : isAns ? "right" : isPicked ? "wrong" : "dim";
              return (
                <button
                  key={o + i}
                  disabled={locked}
                  onMouseEnter={() => sfx.hover()}
                  onClick={() => onAnswer(o)}
                  className={`group relative flex min-h-0 items-center gap-3 overflow-hidden rounded-2xl border px-4 py-2 text-right transition ${
                    state === "idle"
                      ? "glass hover:scale-[1.02] hover:border-gold/60"
                      : state === "right"
                        ? "anim-pop border-transparent bg-gradient-to-l from-emerald-500/80 to-emerald-400/70 text-night"
                        : state === "wrong"
                          ? "anim-shake border-transparent bg-gradient-to-l from-rose-600/80 to-rose-500/70 text-jasmine"
                          : "border-white/10 bg-white/5 opacity-45"
                  }`}
                >
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-current/30 text-xs font-bold">
                    {["أ", "ب", "ج", "د"][i] ?? i + 1}
                  </span>
                  <span className="flex-1 text-sm leading-snug sm:text-base">{o}</span>
                  {state === "right" && <span>✔</span>}
                  {state === "wrong" && <span>✖</span>}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* لوحة النتيجة — طبقة عائمة فوق الشاشة بلا أي تمرير */}
      {feedback && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-night/70 p-4 backdrop-blur-sm">
          <div className="anim-in glass glow-gold w-full max-w-lg rounded-3xl p-5">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className={`font-display text-2xl ${feedback.ok ? "text-emerald-300" : "text-rose-300"}`}>
                  {feedback.ok ? "إجابة صحيحة" : feedback.timeout ? "انتهى الوقت" : "إجابة خاطئة"}
                </p>
                <p className="mt-1 text-sm text-jasmine/80">{feedback.msg}</p>
              </div>
              {feedback.gained > 0 && (
                <span className="anim-pop shrink-0 rounded-full bg-gold/20 px-3 py-1 font-bold text-gold">+{feedback.gained}</span>
              )}
            </div>
            {!feedback.ok && (
              <p className="mt-2 text-sm text-emerald-300">الإجابة الصحيحة: {item.ans}</p>
            )}
            {streak >= 3 && feedback.ok && (
              <p className="mt-2 text-xs text-gold">{pickMsg(STREAK_MSGS, "st")}</p>
            )}
            <p className="mt-3 max-h-32 overflow-y-auto rounded-2xl bg-white/5 p-3 text-xs leading-relaxed text-jasmine/70">💡 {item.q.e}</p>
            <button
              autoFocus
              onClick={onNext}
              className="mt-4 w-full rounded-full bg-gradient-to-l from-gold to-goldsoft py-3 font-extrabold text-night transition hover:scale-[1.02]"
            >
              {index + 1 >= total ? "عرض النتيجة" : "السؤال التالي"}
            </button>
          </div>
        </div>
      )}
    </section>
  );
}


function Results({
  mode,
  players,
  total,
  onReplay,
  onHome,
  onReset,
}: {
  mode: Mode;
  players: [PlayerState, PlayerState];
  total: number;
  onReplay: () => void;
  onHome: () => void;
  onReset: () => void;
}) {
  const list = mode === "duel" ? [players[0], players[1]] : [players[0]];
  const winner = mode === "duel" ? (players[0].score === players[1].score ? null : players[0].score > players[1].score ? 0 : 1) : null;

  const cards = useMemo(
    () =>
      list.map((p) => {
        const best = (Object.keys(p.perCat) as Cat[]).reduce<Cat | null>(
          (acc, c) => (acc === null || p.perCat[c] > p.perCat[acc] ? c : acc),
          null,
        );
        const bestCat = best && p.perCat[best] > 0 ? best : null;
        return { p, id: identityFor(p.score, p.correct, p.answered, bestCat, p.legend), bestCat };
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [players, mode],
  );

  return (
    <section className="anim-in flex min-h-0 flex-1 flex-col justify-center overflow-y-auto py-2">
      <p className="text-center text-xs tracking-[0.35em] text-goldsoft/70">انتهى التحدّي</p>
      <h2 className="mb-4 text-center font-display text-3xl gold-text sm:text-5xl">
        {mode === "duel" ? (winner === null ? "تعادل مشرّف" : `الفائز: ${players[winner].name}`) : "نتيجتك"}
      </h2>

      <div className={`grid gap-4 ${mode === "duel" ? "sm:grid-cols-2" : ""}`}>
        {cards.map(({ p, id, bestCat }, i) => (
          <div key={i} className={`glass rounded-3xl p-5 text-center ${winner === i ? "glow-gold" : ""}`}>
            <div className="text-5xl">{id.icon}</div>
            <h3 className="mt-2 font-display text-2xl text-goldsoft">{id.title}</h3>
            <p className="mt-1 text-xs text-jasmine/65">{id.line}</p>
            <div className="my-4 font-display text-5xl gold-text">{p.score}</div>
            <div className="grid grid-cols-3 gap-2 text-[11px]">
              <Stat label="إجابات صحيحة" value={`${p.correct}/${p.answered}`} />
              <Stat label="أطول سلسلة" value={`${p.best}`} />
              <Stat label="الدقّة" value={`${p.answered ? Math.round((p.correct / p.answered) * 100) : 0}%`} />
            </div>
            {bestCat && (
              <p className="mt-3 text-xs text-jasmine/70">
                أقوى مجال: <span style={{ color: CATS[bestCat].color }}>{CATS[bestCat].icon} {CATS[bestCat].label}</span>
              </p>
            )}
            <p className="mt-1 text-[10px] text-jasmine/40">{p.name} · من أصل {total} سؤالاً</p>
          </div>
        ))}
      </div>

      <div className="mt-5 flex shrink-0 flex-wrap justify-center gap-3">
        <button onClick={onReplay} className="shimmer glow-gold relative overflow-hidden rounded-full bg-gradient-to-l from-gold to-goldsoft px-10 py-3 font-extrabold text-night transition hover:scale-105">
          مباراة جديدة 🔁
        </button>
        <button onClick={onHome} className="glass rounded-full px-8 py-3 text-sm text-jasmine/85">الشاشة الرئيسية</button>
        <button onClick={onReset} className="glass rounded-full px-6 py-3 text-xs text-jasmine/60">تصفير الأسئلة المستخدمة</button>
      </div>
      <p className="mt-4 text-center text-[11px] text-jasmine/40">الأسئلة لا تتكرّر بين الجولات، ومواقع الإجابات تتبدّل في كل مرة.</p>
    </section>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-white/5 px-2 py-2">
      <div className="font-display text-lg text-goldsoft">{value}</div>
      <div className="text-[10px] text-jasmine/55">{label}</div>
    </div>
  );
}
