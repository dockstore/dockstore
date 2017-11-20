--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.10
-- Dumped by pg_dump version 9.5.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

--
-- Name: container_id_seq; Type: SEQUENCE; Schema: public; Owner: dockstore
--

CREATE SEQUENCE container_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE container_id_seq OWNER TO dockstore;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: databasechangelog; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    comments character varying(255),
    contexts character varying(255),
    dateexecuted timestamp without time zone NOT NULL,
    deployment_id character varying(10),
    description character varying(255),
    exectype character varying(10) NOT NULL,
    filename character varying(255) NOT NULL,
    labels character varying(255),
    liquibase character varying(20),
    md5sum character varying(35),
    orderexecuted integer NOT NULL,
    tag character varying(255)
);


ALTER TABLE databasechangelog OWNER TO dockstore;

--
-- Name: databasechangeloglock; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockedby character varying(255),
    lockgranted timestamp without time zone
);


ALTER TABLE databasechangeloglock OWNER TO dockstore;

--
-- Name: enduser; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE enduser (
    id bigint NOT NULL,
    avatarurl character varying(255),
    bio character varying(255),
    company character varying(255),
    email character varying(255),
    isadmin boolean,
    location character varying(255),
    username character varying(255) NOT NULL
);


ALTER TABLE enduser OWNER TO dockstore;

--
-- Name: enduser_id_seq; Type: SEQUENCE; Schema: public; Owner: dockstore
--

CREATE SEQUENCE enduser_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE enduser_id_seq OWNER TO dockstore;

--
-- Name: enduser_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: dockstore
--

ALTER SEQUENCE enduser_id_seq OWNED BY enduser.id;


--
-- Name: endusergroup; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE endusergroup (
    groupid bigint NOT NULL,
    userid bigint NOT NULL
);


ALTER TABLE endusergroup OWNER TO dockstore;

--
-- Name: entry_label; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE entry_label (
    entryid bigint NOT NULL,
    labelid bigint NOT NULL
);


ALTER TABLE entry_label OWNER TO dockstore;

--
-- Name: label; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE label (
    id bigint NOT NULL,
    value character varying(255)
);


ALTER TABLE label OWNER TO dockstore;

--
-- Name: label_id_seq; Type: SEQUENCE; Schema: public; Owner: dockstore
--

CREATE SEQUENCE label_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE label_id_seq OWNER TO dockstore;

--
-- Name: label_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: dockstore
--

ALTER SEQUENCE label_id_seq OWNED BY label.id;


--
-- Name: sourcefile; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE sourcefile (
    id bigint NOT NULL,
    content text,
    path character varying(255) NOT NULL,
    type character varying(255)
);


ALTER TABLE sourcefile OWNER TO dockstore;

--
-- Name: sourcefile_id_seq; Type: SEQUENCE; Schema: public; Owner: dockstore
--

CREATE SEQUENCE sourcefile_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE sourcefile_id_seq OWNER TO dockstore;

--
-- Name: sourcefile_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: dockstore
--

ALTER SEQUENCE sourcefile_id_seq OWNED BY sourcefile.id;


--
-- Name: starred; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE starred (
    userid bigint NOT NULL,
    entryid bigint NOT NULL
);


ALTER TABLE starred OWNER TO dockstore;

--
-- Name: tag; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE tag (
    id bigint NOT NULL,
    dirtybit boolean,
    hidden boolean,
    lastmodified timestamp without time zone,
    name character varying(255),
    reference character varying(255),
    valid boolean,
    verified boolean,
    verifiedsource character varying(255),
    automated boolean,
    cwlpath text NOT NULL,
    dockerfilepath text NOT NULL,
    imageid character varying(255),
    size bigint,
    wdlpath text NOT NULL
);


ALTER TABLE tag OWNER TO dockstore;

--
-- Name: tag_id_seq; Type: SEQUENCE; Schema: public; Owner: dockstore
--

CREATE SEQUENCE tag_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE tag_id_seq OWNER TO dockstore;

--
-- Name: token; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE token (
    id bigint NOT NULL,
    content character varying(255) NOT NULL,
    refreshtoken character varying(255),
    tokensource character varying(255) NOT NULL,
    userid bigint,
    username character varying(255) NOT NULL
);


ALTER TABLE token OWNER TO dockstore;

--
-- Name: token_id_seq; Type: SEQUENCE; Schema: public; Owner: dockstore
--

CREATE SEQUENCE token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE token_id_seq OWNER TO dockstore;

--
-- Name: token_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: dockstore
--

ALTER SEQUENCE token_id_seq OWNED BY token.id;


--
-- Name: tool; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE tool (
    id bigint NOT NULL,
    author character varying(255),
    defaultversion character varying(255),
    description text,
    email character varying(255),
    giturl character varying(255),
    ispublished boolean,
    lastmodified integer,
    lastupdated timestamp without time zone,
    defaultcwlpath text,
    defaultdockerfilepath text,
    defaulttestcwlparameterfile text,
    defaulttestwdlparameterfile text,
    defaultwdlpath text,
    lastbuild timestamp without time zone,
    mode text DEFAULT 'AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS'::text NOT NULL,
    name character varying(255) NOT NULL,
    namespace character varying(255),
    path character varying(255),
    privateaccess boolean,
    registry character varying(255) NOT NULL,
    toolmaintaineremail character varying(255),
    toolname character varying(255) NOT NULL
);


ALTER TABLE tool OWNER TO dockstore;

--
-- Name: tool_tag; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE tool_tag (
    toolid bigint NOT NULL,
    tagid bigint NOT NULL
);


ALTER TABLE tool_tag OWNER TO dockstore;

--
-- Name: user_entry; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE user_entry (
    userid bigint NOT NULL,
    entryid bigint NOT NULL
);


ALTER TABLE user_entry OWNER TO dockstore;

--
-- Name: usergroup; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE usergroup (
    id bigint NOT NULL,
    name character varying(255) NOT NULL
);


ALTER TABLE usergroup OWNER TO dockstore;

--
-- Name: usergroup_id_seq; Type: SEQUENCE; Schema: public; Owner: dockstore
--

CREATE SEQUENCE usergroup_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE usergroup_id_seq OWNER TO dockstore;

--
-- Name: usergroup_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: dockstore
--

ALTER SEQUENCE usergroup_id_seq OWNED BY usergroup.id;


--
-- Name: version_sourcefile; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE version_sourcefile (
    versionid bigint NOT NULL,
    sourcefileid bigint NOT NULL
);


ALTER TABLE version_sourcefile OWNER TO dockstore;

--
-- Name: workflow; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE workflow (
    id bigint NOT NULL,
    author character varying(255),
    defaultversion character varying(255),
    description text,
    email character varying(255),
    giturl character varying(255),
    ispublished boolean,
    lastmodified integer,
    lastupdated timestamp without time zone,
    defaulttestparameterfilepath text,
    defaultworkflowpath text,
    descriptortype character varying(255) NOT NULL,
    mode text DEFAULT 'STUB'::text NOT NULL,
    organization character varying(255) NOT NULL,
    path character varying(255),
    repository character varying(255) NOT NULL,
    workflowname text
);


ALTER TABLE workflow OWNER TO dockstore;

--
-- Name: workflow_workflowversion; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE workflow_workflowversion (
    workflowid bigint NOT NULL,
    workflowversionid bigint NOT NULL
);


ALTER TABLE workflow_workflowversion OWNER TO dockstore;

--
-- Name: workflowversion; Type: TABLE; Schema: public; Owner: dockstore
--

CREATE TABLE workflowversion (
    id bigint NOT NULL,
    dirtybit boolean,
    hidden boolean,
    lastmodified timestamp without time zone,
    name character varying(255),
    reference character varying(255),
    valid boolean,
    verified boolean,
    verifiedsource character varying(255),
    workflowpath text NOT NULL
);


ALTER TABLE workflowversion OWNER TO dockstore;

--
-- Name: id; Type: DEFAULT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY enduser ALTER COLUMN id SET DEFAULT nextval('enduser_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY label ALTER COLUMN id SET DEFAULT nextval('label_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY sourcefile ALTER COLUMN id SET DEFAULT nextval('sourcefile_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY token ALTER COLUMN id SET DEFAULT nextval('token_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY usergroup ALTER COLUMN id SET DEFAULT nextval('usergroup_id_seq'::regclass);


--
-- Name: databasechangelog_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY databasechangelog
    ADD CONSTRAINT databasechangelog_pkey PRIMARY KEY (id);


--
-- Name: databasechangeloglock_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY databasechangeloglock
    ADD CONSTRAINT databasechangeloglock_pkey PRIMARY KEY (id);


--
-- Name: enduser_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY enduser
    ADD CONSTRAINT enduser_pkey PRIMARY KEY (id);


--
-- Name: endusergroup_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY endusergroup
    ADD CONSTRAINT endusergroup_pkey PRIMARY KEY (userid, groupid);


--
-- Name: entry_label_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY entry_label
    ADD CONSTRAINT entry_label_pkey PRIMARY KEY (entryid, labelid);


--
-- Name: label_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY label
    ADD CONSTRAINT label_pkey PRIMARY KEY (id);


--
-- Name: sourcefile_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY sourcefile
    ADD CONSTRAINT sourcefile_pkey PRIMARY KEY (id);


--
-- Name: starred_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY starred
    ADD CONSTRAINT starred_pkey PRIMARY KEY (entryid, userid);


--
-- Name: tag_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY tag
    ADD CONSTRAINT tag_pkey PRIMARY KEY (id);


--
-- Name: token_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY token
    ADD CONSTRAINT token_pkey PRIMARY KEY (id);


--
-- Name: tool_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY tool
    ADD CONSTRAINT tool_pkey PRIMARY KEY (id);


--
-- Name: tool_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY tool_tag
    ADD CONSTRAINT tool_tag_pkey PRIMARY KEY (toolid, tagid);


--
-- Name: uk_9vcoeu4nuu2ql7fh05mn20ydd; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY enduser
    ADD CONSTRAINT uk_9vcoeu4nuu2ql7fh05mn20ydd UNIQUE (username);


--
-- Name: uk_9xhsn1bsea2csoy3l0gtq41vv; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY label
    ADD CONSTRAINT uk_9xhsn1bsea2csoy3l0gtq41vv UNIQUE (value);


--
-- Name: uk_e2j71kjdot9b8l5qmjw2ve38o; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY version_sourcefile
    ADD CONSTRAINT uk_e2j71kjdot9b8l5qmjw2ve38o UNIQUE (sourcefileid);


--
-- Name: uk_encl8hnebnkcaxj9tlugr9cxh; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY workflow_workflowversion
    ADD CONSTRAINT uk_encl8hnebnkcaxj9tlugr9cxh UNIQUE (workflowversionid);


--
-- Name: uk_jdgfioq44aqox39xrs1wceow1; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY tool_tag
    ADD CONSTRAINT uk_jdgfioq44aqox39xrs1wceow1 UNIQUE (tagid);


--
-- Name: ukbq5vy17y4ocaist3d3r3imcus; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY tool
    ADD CONSTRAINT ukbq5vy17y4ocaist3d3r3imcus UNIQUE (registry, namespace, name, toolname);


--
-- Name: ukkprrtg54h6rjca5l1navospm8; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY workflow
    ADD CONSTRAINT ukkprrtg54h6rjca5l1navospm8 UNIQUE (organization, repository, workflowname);


--
-- Name: user_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY user_entry
    ADD CONSTRAINT user_entry_pkey PRIMARY KEY (entryid, userid);


--
-- Name: usergroup_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY usergroup
    ADD CONSTRAINT usergroup_pkey PRIMARY KEY (id);


--
-- Name: version_sourcefile_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY version_sourcefile
    ADD CONSTRAINT version_sourcefile_pkey PRIMARY KEY (versionid, sourcefileid);


--
-- Name: workflow_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY workflow
    ADD CONSTRAINT workflow_pkey PRIMARY KEY (id);


--
-- Name: workflow_workflowversion_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY workflow_workflowversion
    ADD CONSTRAINT workflow_workflowversion_pkey PRIMARY KEY (workflowid, workflowversionid);


--
-- Name: workflowversion_pkey; Type: CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY workflowversion
    ADD CONSTRAINT workflowversion_pkey PRIMARY KEY (id);


--
-- Name: full_tool_name; Type: INDEX; Schema: public; Owner: dockstore
--

CREATE UNIQUE INDEX full_tool_name ON tool USING btree (registry, namespace, name, toolname) WHERE (toolname IS NOT NULL);


--
-- Name: full_workflow_name; Type: INDEX; Schema: public; Owner: dockstore
--

CREATE UNIQUE INDEX full_workflow_name ON workflow USING btree (organization, repository, workflowname) WHERE (workflowname IS NOT NULL);


--
-- Name: partial_tool_name; Type: INDEX; Schema: public; Owner: dockstore
--

CREATE UNIQUE INDEX partial_tool_name ON tool USING btree (registry, namespace, name) WHERE (toolname IS NULL);


--
-- Name: partial_workflow_name; Type: INDEX; Schema: public; Owner: dockstore
--

CREATE UNIQUE INDEX partial_workflow_name ON workflow USING btree (organization, repository) WHERE (workflowname IS NULL);


--
-- Name: fkdcfqiy0arvxmmh5e68ix75gwo; Type: FK CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY starred
    ADD CONSTRAINT fkdcfqiy0arvxmmh5e68ix75gwo FOREIGN KEY (userid) REFERENCES enduser(id);


--
-- Name: fkhdtovkjeuj2u4adc073nh02w; Type: FK CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY user_entry
    ADD CONSTRAINT fkhdtovkjeuj2u4adc073nh02w FOREIGN KEY (userid) REFERENCES enduser(id);


--
-- Name: fkibmeux3552ua8dwnqdb8w6991; Type: FK CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY workflow_workflowversion
    ADD CONSTRAINT fkibmeux3552ua8dwnqdb8w6991 FOREIGN KEY (workflowversionid) REFERENCES workflowversion(id);


--
-- Name: fkjkn6qubuvn25bun52eqjleyl6; Type: FK CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY tool_tag
    ADD CONSTRAINT fkjkn6qubuvn25bun52eqjleyl6 FOREIGN KEY (tagid) REFERENCES tag(id);


--
-- Name: fkjtsjg6jdnwxoeicd27ujmeeaj; Type: FK CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY tool_tag
    ADD CONSTRAINT fkjtsjg6jdnwxoeicd27ujmeeaj FOREIGN KEY (toolid) REFERENCES tool(id);


--
-- Name: fkl8yg13ahjhtn0notrlf3amwwi; Type: FK CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY workflow_workflowversion
    ADD CONSTRAINT fkl8yg13ahjhtn0notrlf3amwwi FOREIGN KEY (workflowid) REFERENCES workflow(id);


--
-- Name: fkm0exig2r3dsxqafwaraf7rnr3; Type: FK CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY endusergroup
    ADD CONSTRAINT fkm0exig2r3dsxqafwaraf7rnr3 FOREIGN KEY (groupid) REFERENCES usergroup(id);


--
-- Name: fkmby5o476bdwrx07ax2keoyttn; Type: FK CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY version_sourcefile
    ADD CONSTRAINT fkmby5o476bdwrx07ax2keoyttn FOREIGN KEY (sourcefileid) REFERENCES sourcefile(id);


--
-- Name: fkrxn6hh2max4sk4ceehyv7mt2e; Type: FK CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY endusergroup
    ADD CONSTRAINT fkrxn6hh2max4sk4ceehyv7mt2e FOREIGN KEY (userid) REFERENCES enduser(id);


--
-- Name: fks71c9mk0f98015eqgtyvs0ewp; Type: FK CONSTRAINT; Schema: public; Owner: dockstore
--

ALTER TABLE ONLY entry_label
    ADD CONSTRAINT fks71c9mk0f98015eqgtyvs0ewp FOREIGN KEY (labelid) REFERENCES label(id);


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

