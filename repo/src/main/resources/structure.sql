CREATE TABLE public.dbsecret (
    passphrase text
);

CREATE SEQUENCE public.hibernate_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.hook (
    id integer NOT NULL,
    active boolean NOT NULL,
    target integer NOT NULL,
    type character varying(255) NOT NULL,
    url character varying(255) NOT NULL
);

CREATE SEQUENCE public.hook_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.run (
    id integer NOT NULL,
    data jsonb NOT NULL,
    start timestamp without time zone NOT NULL,
    stop timestamp without time zone NOT NULL,
    testid integer NOT NULL,
    owner text NOT NULL,
    access integer,
    token text
);

CREATE SEQUENCE public.run_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.schema (
    id integer NOT NULL,
    uri text NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    schema jsonb,
    testPath text,
    startPath text,
    stopPath text,
    view jsonb,
    owner text NOT NULL,
    token text,
    access integer NOT NULL
);

CREATE SEQUENCE public.schema_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.schemaextractor (
    id integer NOT NULL,
    accessor character varying(255) NOT NULL,
    jsonpath character varying(255) NOT NULL,
    schema_id integer NOT NULL
);

CREATE TABLE public.test (
    id integer NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    owner text NOT NULL,
    access integer NOT NULL,
    token text,
    defaultview_id integer
);

CREATE SEQUENCE public.test_id_seq
    START WITH 10
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.view (
    id integer NOT NULL,
    name character varying(255) NOT NULL,
    test_id integer
);

CREATE TABLE public.viewcomponent (
    id integer NOT NULL,
    accessors character varying(255) NOT NULL,
    headername character varying(255) NOT NULL,
    headerorder integer NOT NULL,
    render text,
    view_id integer,
);

ALTER TABLE ONLY public.hook
    ADD CONSTRAINT hook_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.run
    ADD CONSTRAINT run_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.schema
    ADD CONSTRAINT schema_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.schema
    ADD CONSTRAINT unique_uri UNIQUE (uri);

ALTER TABLE ONLY public.test
    ADD CONSTRAINT test_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.test
    ADD CONSTRAINT uk_1chowmbf27f0cbp7on9ysvhjo UNIQUE (name);

ALTER TABLE ONLY public.test
    ADD CONSTRAINT fkd8gmrsjed82jkhve746yde52g FOREIGN KEY (defaultview_id) REFERENCES public.view(id);

ALTER TABLE ONLY public.schema
    ADD CONSTRAINT uk_ercynxu2sv11igxixldcwgexv UNIQUE (name);

ALTER TABLE ONLY public.schemaextractor
    ADD CONSTRAINT schemaextractor_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.schemaextractor
    ADD CONSTRAINT fkjc7utffrgr1r7dtae28vfjdvh FOREIGN KEY (schema_id) REFERENCES public.schema(id);

ALTER TABLE ONLY public.hook
    ADD CONSTRAINT ukdnk21cxs9g7qg69sgl0wsmbnw UNIQUE (url, type, target);

ALTER TABLE ONLY public.view
    ADD CONSTRAINT view_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.view
    ADD CONSTRAINT fkbk3pl5stm968dhcyjaa9aih94 FOREIGN KEY (test_id) REFERENCES public.test(id);

ALTER TABLE ONLY public.viewcomponent
    ADD CONSTRAINT uktrrs6gtljsyiwepfggjrmltfw UNIQUE (view_id, headername);

ALTER TABLE ONLY public.viewcomponent
    ADD CONSTRAINT viewcomponent_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.viewcomponent
    ADD CONSTRAINT fkkaa8vdeo8hkvxdljf2a2gufjc FOREIGN KEY (view_id) REFERENCES public.view(id);
