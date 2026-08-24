CREATE TABLE public.scores (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  player_name TEXT NOT NULL CHECK (char_length(player_name) BETWEEN 1 AND 24),
  score INTEGER NOT NULL CHECK (score >= 0 AND score <= 100000),
  correct INTEGER NOT NULL DEFAULT 0 CHECK (correct >= 0),
  answered INTEGER NOT NULL DEFAULT 0 CHECK (answered >= 0),
  best_streak INTEGER NOT NULL DEFAULT 0 CHECK (best_streak >= 0),
  difficulty TEXT NOT NULL DEFAULT 'normal' CHECK (difficulty IN ('normal','hard','legend')),
  mode TEXT NOT NULL DEFAULT 'solo' CHECK (mode IN ('solo','duel')),
  identity_title TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT ON public.scores TO anon;
GRANT SELECT, INSERT ON public.scores TO authenticated;
GRANT ALL ON public.scores TO service_role;

ALTER TABLE public.scores ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Anyone can view scores" ON public.scores FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "Anyone can submit a score" ON public.scores FOR INSERT TO anon, authenticated WITH CHECK (true);

CREATE INDEX scores_leaderboard_idx ON public.scores (score DESC, created_at DESC);