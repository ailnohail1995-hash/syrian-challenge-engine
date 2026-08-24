import { supabase } from "@/integrations/supabase/client";

export interface ScoreRow {
  id: string;
  player_name: string;
  score: number;
  correct: number;
  answered: number;
  best_streak: number;
  difficulty: string;
  mode: string;
  identity_title: string | null;
  created_at: string;
}

export interface ScoreInput {
  player_name: string;
  score: number;
  correct: number;
  answered: number;
  best_streak: number;
  difficulty: string;
  mode: string;
  identity_title?: string | null;
}

/** حفظ النتيجة أونلاين (لوحة الصدارة العامة) */
export async function submitScores(rows: ScoreInput[]): Promise<boolean> {
  const clean = rows
    .filter((r) => r.answered > 0)
    .map((r) => ({
      ...r,
      player_name: (r.player_name || "لاعب").slice(0, 24),
      score: Math.max(0, Math.min(100000, Math.round(r.score))),
    }));
  if (!clean.length) return false;
  const { error } = await supabase.from("scores").insert(clean);
  if (error) {
    console.error("submitScores", error.message);
    return false;
  }
  return true;
}

/** أفضل النتائج */
export async function fetchTopScores(limit = 20): Promise<ScoreRow[]> {
  const { data, error } = await supabase
    .from("scores")
    .select("*")
    .order("score", { ascending: false })
    .order("created_at", { ascending: false })
    .limit(limit);
  if (error) {
    console.error("fetchTopScores", error.message);
    return [];
  }
  return (data ?? []) as ScoreRow[];
}
