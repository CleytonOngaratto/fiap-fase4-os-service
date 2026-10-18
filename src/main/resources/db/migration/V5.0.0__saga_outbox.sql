CREATE TABLE IF NOT EXISTS public.saga_outbox
(
    message_id     uuid         NOT NULL,
    work_order_id  int8         NOT NULL,
    saga_id        uuid         NOT NULL,
    type           varchar(60)  NOT NULL,
    version        int4         NOT NULL,
    occurred_at    timestamptz  NOT NULL,
    correlation_id varchar(120),
    causation_id   uuid,
    payload        text         NOT NULL,
    sent_at        timestamptz,
    CONSTRAINT saga_outbox_pkey PRIMARY KEY (message_id)
);

CREATE INDEX IF NOT EXISTS saga_outbox_pending_idx
    ON public.saga_outbox (occurred_at)
    WHERE sent_at IS NULL;
