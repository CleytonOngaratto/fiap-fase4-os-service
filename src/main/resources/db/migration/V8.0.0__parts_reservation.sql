ALTER TABLE public.parts_and_supply
    ADD COLUMN IF NOT EXISTS reserved_quantity int4 NOT NULL DEFAULT 0;
