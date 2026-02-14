--
-- PostgreSQL database dump
--

\restrict CQUuxNkkwBDcEcWSDYVEWXvXBYB1PJrmKKw8cX019fpkHuMcBbUE4Tf1DR17IIg

-- Dumped from database version 18.2 (Ubuntu 18.2-1.pgdg24.04+1)
-- Dumped by pg_dump version 18.2 (Ubuntu 18.2-1.pgdg24.04+1)

-- Started on 2026-02-15 01:46:07 CET

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 6 (class 2615 OID 2200)
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- TOC entry 2 (class 3079 OID 17406)
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- TOC entry 3534 (class 0 OID 0)
-- Dependencies: 2
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 220 (class 1259 OID 17417)
-- Name: album; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.album (
    id uuid NOT NULL,
    name character varying NOT NULL,
    custom_question character varying
);


--
-- TOC entry 225 (class 1259 OID 17478)
-- Name: category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.category (
    id uuid NOT NULL,
    album_id uuid NOT NULL,
    game_id uuid NOT NULL,
    picked_by_team_id uuid,
    ordinal_number integer,
    is_done boolean
);


--
-- TOC entry 221 (class 1259 OID 17426)
-- Name: game; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.game (
    id uuid NOT NULL,
    date timestamp without time zone NOT NULL,
    stage integer DEFAULT 0 CONSTRAINT game_status_not_null NOT NULL,
    max_songs integer DEFAULT 10 NOT NULL,
    max_albums integer DEFAULT 10 NOT NULL,
    code character varying(4),
    password_hash character(128)
);


--
-- TOC entry 227 (class 1259 OID 17517)
-- Name: interrupt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.interrupt (
    id uuid NOT NULL,
    schedule_id uuid,
    arrived_at timestamp without time zone,
    resolved_at timestamp without time zone,
    is_correct boolean,
    score_or_scenario_id integer,
    team_id uuid
);


--
-- TOC entry 226 (class 1259 OID 17501)
-- Name: schedule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schedule (
    id uuid NOT NULL,
    ordinal_number integer,
    track_id uuid,
    started_at timestamp without time zone,
    revealed_at timestamp without time zone,
    category_id uuid
);


--
-- TOC entry 223 (class 1259 OID 17452)
-- Name: song; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.song (
    id uuid NOT NULL,
    authors character varying,
    name character varying,
    snippet_duration double precision,
    answer_duration double precision
);


--
-- TOC entry 222 (class 1259 OID 17439)
-- Name: team; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.team (
    id uuid NOT NULL,
    button_code text,
    game_id uuid,
    name character varying,
    image character varying
);


--
-- TOC entry 224 (class 1259 OID 17460)
-- Name: track; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.track (
    id uuid NOT NULL,
    album_id uuid,
    song_id uuid,
    custom_answer character varying
);


--
-- TOC entry 3352 (class 2606 OID 17425)
-- Name: album album_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album
    ADD CONSTRAINT album_pkey PRIMARY KEY (id);


--
-- TOC entry 3364 (class 2606 OID 17485)
-- Name: category category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT category_pkey PRIMARY KEY (id);


--
-- TOC entry 3354 (class 2606 OID 17438)
-- Name: game game_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game
    ADD CONSTRAINT game_pkey PRIMARY KEY (id);


--
-- TOC entry 3371 (class 2606 OID 17522)
-- Name: interrupt interrupt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.interrupt
    ADD CONSTRAINT interrupt_pkey PRIMARY KEY (id);


--
-- TOC entry 3366 (class 2606 OID 17506)
-- Name: schedule schedule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedule
    ADD CONSTRAINT schedule_pkey PRIMARY KEY (id);


--
-- TOC entry 3360 (class 2606 OID 17459)
-- Name: song song_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.song
    ADD CONSTRAINT song_pkey PRIMARY KEY (id);


--
-- TOC entry 3358 (class 2606 OID 17446)
-- Name: team team_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team
    ADD CONSTRAINT team_pkey PRIMARY KEY (id);


--
-- TOC entry 3362 (class 2606 OID 17467)
-- Name: track track_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.track
    ADD CONSTRAINT track_pkey PRIMARY KEY (id);


--
-- TOC entry 3367 (class 1259 OID 17534)
-- Name: idx_interrupt_schedule_arrived_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_interrupt_schedule_arrived_time ON public.interrupt USING btree (schedule_id, arrived_at DESC);


--
-- TOC entry 3368 (class 1259 OID 17533)
-- Name: idx_interrupt_team_schedule; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_interrupt_team_schedule ON public.interrupt USING btree (team_id, schedule_id);


--
-- TOC entry 3356 (class 1259 OID 17537)
-- Name: idx_team_button_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_team_button_code ON public.team USING btree (button_code);


--
-- TOC entry 3355 (class 1259 OID 17535)
-- Name: index_game_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_game_code ON public.game USING btree (code) WITH (deduplicate_items='true');


--
-- TOC entry 3369 (class 1259 OID 17536)
-- Name: interrupt_index_arrived_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX interrupt_index_arrived_desc ON public.interrupt USING btree (arrived_at DESC) WITH (deduplicate_items='true');


--
-- TOC entry 3375 (class 2606 OID 17486)
-- Name: category fk_category_album; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT fk_category_album FOREIGN KEY (album_id) REFERENCES public.album(id);


--
-- TOC entry 3376 (class 2606 OID 17491)
-- Name: category fk_category_game; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT fk_category_game FOREIGN KEY (game_id) REFERENCES public.game(id);


--
-- TOC entry 3377 (class 2606 OID 17496)
-- Name: category fk_category_team; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT fk_category_team FOREIGN KEY (picked_by_team_id) REFERENCES public.team(id);


--
-- TOC entry 3380 (class 2606 OID 17523)
-- Name: interrupt fk_interrupt_schedule; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.interrupt
    ADD CONSTRAINT fk_interrupt_schedule FOREIGN KEY (schedule_id) REFERENCES public.schedule(id);


--
-- TOC entry 3381 (class 2606 OID 17528)
-- Name: interrupt fk_interrupt_team; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.interrupt
    ADD CONSTRAINT fk_interrupt_team FOREIGN KEY (team_id) REFERENCES public.team(id);


--
-- TOC entry 3378 (class 2606 OID 17507)
-- Name: schedule fk_schedule_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedule
    ADD CONSTRAINT fk_schedule_category FOREIGN KEY (category_id) REFERENCES public.category(id);


--
-- TOC entry 3379 (class 2606 OID 17512)
-- Name: schedule fk_schedule_track; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedule
    ADD CONSTRAINT fk_schedule_track FOREIGN KEY (track_id) REFERENCES public.track(id);


--
-- TOC entry 3372 (class 2606 OID 17447)
-- Name: team fk_team_game; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team
    ADD CONSTRAINT fk_team_game FOREIGN KEY (game_id) REFERENCES public.game(id);


--
-- TOC entry 3373 (class 2606 OID 17468)
-- Name: track fk_track_album; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.track
    ADD CONSTRAINT fk_track_album FOREIGN KEY (album_id) REFERENCES public.album(id);


--
-- TOC entry 3374 (class 2606 OID 17473)
-- Name: track fk_track_song; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.track
    ADD CONSTRAINT fk_track_song FOREIGN KEY (song_id) REFERENCES public.song(id);


-- Completed on 2026-02-15 01:46:07 CET

--
-- PostgreSQL database dump complete
--

\unrestrict CQUuxNkkwBDcEcWSDYVEWXvXBYB1PJrmKKw8cX019fpkHuMcBbUE4Tf1DR17IIg

