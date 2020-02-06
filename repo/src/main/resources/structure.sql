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
    schemaUri text,
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

CREATE TABLE public.test (
    id integer NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    schema jsonb,
    view jsonb,
    owner text NOT NULL,
    access integer NOT NULL,
    token text
);

CREATE SEQUENCE public.test_id_seq
    START WITH 10
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

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

ALTER TABLE ONLY public.schema
    ADD CONSTRAINT uk_ercynxu2sv11igxixldcwgexv UNIQUE (name);

ALTER TABLE ONLY public.hook
    ADD CONSTRAINT ukdnk21cxs9g7qg69sgl0wsmbnw UNIQUE (url, type, target);

