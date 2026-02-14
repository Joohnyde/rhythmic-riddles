--
-- PostgreSQL database dump
--

\restrict ITbFNdgDQG2zadIAg7xj6hXPF4ygnNrSJJxcKzhho8nJbNLT4ru2fMmge4S8sWP

-- Dumped from database version 18.2 (Ubuntu 18.2-1.pgdg24.04+1)
-- Dumped by pg_dump version 18.2 (Ubuntu 18.2-1.pgdg24.04+1)

-- Started on 2026-02-15 01:56:56 CET

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
-- TOC entry 3526 (class 0 OID 17417)
-- Dependencies: 220
-- Data for Name: album; Type: TABLE DATA; Schema: public;
--

INSERT INTO public.album VALUES ('edf940fb-250d-4f5a-ad61-f1d4f254a435', 'Album1', NULL);
INSERT INTO public.album VALUES ('3c8ed2c0-df3d-4639-b6a6-81550e473cca', 'Album2', NULL);
INSERT INTO public.album VALUES ('c18a96dd-4991-4e28-9c04-d61ca05bc81b', 'Album3', NULL);
INSERT INTO public.album VALUES ('6214ed07-03df-41c7-a1fa-b1a9b9e9bd01', 'Album4', NULL);
INSERT INTO public.album VALUES ('11f109d1-e4ac-4dd8-99fe-108035273677', 'Album5', NULL);


--
-- TOC entry 3527 (class 0 OID 17426)
-- Dependencies: 221
-- Data for Name: game; Type: TABLE DATA; Schema: public;
--

INSERT INTO public.game VALUES ('b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', '2025-12-05 23:09:25.405306', 3, 2, 3, 'AKKU', NULL);
INSERT INTO public.game VALUES ('aac328fb-be69-4f36-aa2d-a2839acf8360', '2025-12-02 17:34:57.56428', 3, 1, 3, 'NORR', NULL);


--
-- TOC entry 3528 (class 0 OID 17439)
-- Dependencies: 222
-- Data for Name: team; Type: TABLE DATA; Schema: public;
--

INSERT INTO public.team VALUES ('17599f50-9e9d-4080-9077-b045e560db37', '123', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', 'Treci', 'https://i.imgur.com/OorBALw.png');
INSERT INTO public.team VALUES ('7f0612f5-cb1d-4665-8424-ef0027f355c7', '124', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', 'Luka', 'https://i.imgur.com/rV2ENEv.png');
INSERT INTO public.team VALUES ('9e604591-7a9f-4370-ac6f-595f6147498c', '125', 'aac328fb-be69-4f36-aa2d-a2839acf8360', 'Nazis', 'https://i.imgur.com/qoFrBsU.png');
INSERT INTO public.team VALUES ('e9c7ce08-a8cd-41bd-8716-37d774ab0132', '215', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', 'Deni', 'https://i.imgur.com/7oulgAX.png');
INSERT INTO public.team VALUES ('77272fb1-09f0-4db1-a410-b6f467441c22', '1671', 'aac328fb-be69-4f36-aa2d-a2839acf8360', 'Sugavac', 'https://i.imgur.com/p4E6G89.png');


--
-- TOC entry 3531 (class 0 OID 17478)
-- Dependencies: 225
-- Data for Name: category; Type: TABLE DATA; Schema: public;
--

INSERT INTO public.category VALUES ('2bbd4516-e0f1-492b-a12f-bc3cde999dc1', '3c8ed2c0-df3d-4639-b6a6-81550e473cca', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', NULL, NULL, false);
INSERT INTO public.category VALUES ('01766a15-e6bc-444a-a5a7-cd1b8f48ef40', '11f109d1-e4ac-4dd8-99fe-108035273677', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', NULL, NULL, false);
INSERT INTO public.category VALUES ('2d3958b2-b03e-4bfd-844a-54e507e95043', '6214ed07-03df-41c7-a1fa-b1a9b9e9bd01', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', NULL, NULL, false);
INSERT INTO public.category VALUES ('68f384d3-e437-45e3-b879-cc2cd9a31be3', 'c18a96dd-4991-4e28-9c04-d61ca05bc81b', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', NULL, NULL, false);
INSERT INTO public.category VALUES ('647594c1-a18a-42c7-b59c-07ffb1d06d32', 'edf940fb-250d-4f5a-ad61-f1d4f254a435', 'aac328fb-be69-4f36-aa2d-a2839acf8360', NULL, NULL, false);
INSERT INTO public.category VALUES ('ac7157fd-47a7-4848-b8b5-e8741cb0a5f6', '6214ed07-03df-41c7-a1fa-b1a9b9e9bd01', 'aac328fb-be69-4f36-aa2d-a2839acf8360', NULL, NULL, false);
INSERT INTO public.category VALUES ('ec6c9cf3-c759-4a12-9763-530bb76e9f54', '3c8ed2c0-df3d-4639-b6a6-81550e473cca', 'aac328fb-be69-4f36-aa2d-a2839acf8360', NULL, NULL, false);


--
-- TOC entry 3529 (class 0 OID 17452)
-- Dependencies: 223
-- Data for Name: song; Type: TABLE DATA; Schema: public;
--

INSERT INTO public.song VALUES ('c041398e-8e63-40ed-8f17-d7f1ca8ca405', 'Relja Torinno', 'i8', 9.01, 6);
INSERT INTO public.song VALUES ('51ebda2d-056e-407c-8a71-352a88fa0136', 'All Time Low', 'Monsters', 11.75, 6.03);
INSERT INTO public.song VALUES ('5a5096a5-14ed-4b46-ae6f-75ce8ed8df70', 'Zdravko Colic', 'Flamingosi', 11.54, 6.97);


--
-- TOC entry 3530 (class 0 OID 17460)
-- Dependencies: 224
-- Data for Name: track; Type: TABLE DATA; Schema: public;
--

INSERT INTO public.track VALUES ('8c2a62d9-271a-4a36-bb22-5fb6b74d28c7', 'edf940fb-250d-4f5a-ad61-f1d4f254a435', '5a5096a5-14ed-4b46-ae6f-75ce8ed8df70', NULL);
INSERT INTO public.track VALUES ('b4e9173e-e68c-465e-b155-98cc2a01c3e8', 'edf940fb-250d-4f5a-ad61-f1d4f254a435', 'c041398e-8e63-40ed-8f17-d7f1ca8ca405', NULL);
INSERT INTO public.track VALUES ('1cd1bb6e-0e26-4144-a4a4-ab2f5db3aaa2', '3c8ed2c0-df3d-4639-b6a6-81550e473cca', '5a5096a5-14ed-4b46-ae6f-75ce8ed8df70', NULL);
INSERT INTO public.track VALUES ('929325b4-3bcf-4d58-b7d8-b9fcff347781', 'c18a96dd-4991-4e28-9c04-d61ca05bc81b', '5a5096a5-14ed-4b46-ae6f-75ce8ed8df70', NULL);
INSERT INTO public.track VALUES ('4485e157-bf42-46f0-a961-494a7686e13c', 'c18a96dd-4991-4e28-9c04-d61ca05bc81b', 'c041398e-8e63-40ed-8f17-d7f1ca8ca405', NULL);
INSERT INTO public.track VALUES ('f77d94e0-dfdf-4ca9-ae0b-dad8b81c85df', '6214ed07-03df-41c7-a1fa-b1a9b9e9bd01', 'c041398e-8e63-40ed-8f17-d7f1ca8ca405', NULL);
INSERT INTO public.track VALUES ('02ae2162-6180-4ede-80d5-79b536073524', '6214ed07-03df-41c7-a1fa-b1a9b9e9bd01', '51ebda2d-056e-407c-8a71-352a88fa0136', NULL);
INSERT INTO public.track VALUES ('1d0131ca-1417-4b60-b524-51b25652e997', '11f109d1-e4ac-4dd8-99fe-108035273677', '51ebda2d-056e-407c-8a71-352a88fa0136', NULL);
INSERT INTO public.track VALUES ('8168ba9e-68c6-4e98-aa47-7d28e72b33ab', '3c8ed2c0-df3d-4639-b6a6-81550e473cca', '51ebda2d-056e-407c-8a71-352a88fa0136', NULL);
INSERT INTO public.track VALUES ('a6aa901c-2cad-4571-81ff-398ee935b10a', '11f109d1-e4ac-4dd8-99fe-108035273677', '51ebda2d-056e-407c-8a71-352a88fa0136', NULL);


--
-- TOC entry 3532 (class 0 OID 17501)
-- Dependencies: 226
-- Data for Name: schedule; Type: TABLE DATA; Schema: public;
--



--
-- TOC entry 3533 (class 0 OID 17517)
-- Dependencies: 227
-- Data for Name: interrupt; Type: TABLE DATA; Schema: public;
--



-- Completed on 2026-02-15 01:56:56 CET

--
-- PostgreSQL database dump complete
--

\unrestrict ITbFNdgDQG2zadIAg7xj6hXPF4ygnNrSJJxcKzhho8nJbNLT4ru2fMmge4S8sWP

