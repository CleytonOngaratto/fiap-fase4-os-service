CREATE TABLE IF NOT EXISTS public.branches
(
    id   varchar(10)  NOT NULL,
    name varchar(120) NOT NULL,
    city varchar(120) NOT NULL,
    CONSTRAINT branches_pkey PRIMARY KEY (id)
);

INSERT INTO public.branches (id, name, city)
VALUES ('SP-01', 'Unidade Paulista', 'Sao Paulo'),
       ('SP-02', 'Unidade Santo Amaro', 'Sao Paulo'),
       ('RJ-01', 'Unidade Barra', 'Rio de Janeiro')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.work_orders
    ADD COLUMN IF NOT EXISTS branch_id         varchar(10),
    ADD COLUMN IF NOT EXISTS customer_document varchar(255),
    ADD COLUMN IF NOT EXISTS complaint         varchar(500),
    ADD COLUMN IF NOT EXISTS cancel_reason     varchar(40);

UPDATE public.work_orders wo
SET customer_document = c.document
FROM public.customers c
WHERE wo.customer_id = c.id
  AND wo.customer_document IS NULL;

ALTER TABLE public.work_orders
    DROP CONSTRAINT IF EXISTS fk_branch_work_orders;

ALTER TABLE public.work_orders
    ADD CONSTRAINT fk_branch_work_orders FOREIGN KEY (branch_id) REFERENCES public.branches (id);

CREATE TABLE IF NOT EXISTS public.work_order_status_history
(
    id            int8        NOT NULL,
    work_order_id int8        NOT NULL,
    branch_id     varchar(10),
    status        varchar(40) NOT NULL,
    reason        varchar(40),
    saga_id       uuid,
    changed_at    timestamptz NOT NULL,
    CONSTRAINT work_order_status_history_pkey PRIMARY KEY (id),
    CONSTRAINT fk_work_order_status_history FOREIGN KEY (work_order_id) REFERENCES public.work_orders (id)
);

CREATE SEQUENCE IF NOT EXISTS public.work_order_status_history_seq
    AS int8
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE CACHE 1;

CREATE INDEX IF NOT EXISTS work_order_status_history_idx
    ON public.work_order_status_history (work_order_id, changed_at);
