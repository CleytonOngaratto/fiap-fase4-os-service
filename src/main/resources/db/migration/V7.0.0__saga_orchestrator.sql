CREATE TABLE IF NOT EXISTS public.saga_instance
(
    saga_id                uuid        NOT NULL,
    work_order_id          int8        NOT NULL,
    branch_id              varchar(10),
    state                  varchar(40) NOT NULL,
    cancel_reason          varchar(40),
    budget_id              varchar(60),
    payment_id             varchar(60),
    pending_compensations  varchar(200),
    deadline_at            timestamptz,
    version                int8        NOT NULL,
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL,
    CONSTRAINT saga_instance_pkey PRIMARY KEY (saga_id),
    CONSTRAINT saga_instance_work_order_uk UNIQUE (work_order_id)
);

CREATE INDEX IF NOT EXISTS saga_instance_deadline_idx
    ON public.saga_instance (deadline_at)
    WHERE deadline_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.saga_inbox
(
    message_id    uuid        NOT NULL,
    work_order_id int8        NOT NULL,
    type          varchar(60) NOT NULL,
    processed_at  timestamptz NOT NULL,
    CONSTRAINT saga_inbox_pkey PRIMARY KEY (message_id)
);

ALTER TABLE public.saga_outbox
    ADD COLUMN IF NOT EXISTS branch_id   varchar(10),
    ADD COLUMN IF NOT EXISTS destination varchar(20);

UPDATE public.saga_outbox
SET destination = 'EXECUTION'
WHERE destination IS NULL
  AND type IN ('StartDiagnosis', 'CancelDiagnosis', 'StartRepair');

UPDATE public.saga_outbox
SET destination = 'BILLING'
WHERE destination IS NULL
  AND type IN ('CreateBudget', 'CancelBudget', 'RequestPayment', 'CancelPayment', 'RefundPayment');

-- O default existe para o rollout: o pod novo aplica esta migration enquanto os antigos ainda inserem no
-- outbox sem a coluna, e sem ele o INSERT deles falharia e a abertura de OS devolveria 500.
ALTER TABLE public.saga_outbox
    ALTER COLUMN destination SET DEFAULT 'EXECUTION';

ALTER TABLE public.saga_outbox
    ALTER COLUMN destination SET NOT NULL;

DROP INDEX IF EXISTS public.saga_outbox_pending_idx;

CREATE INDEX IF NOT EXISTS saga_outbox_pending_idx
    ON public.saga_outbox (destination, occurred_at)
    WHERE sent_at IS NULL;
