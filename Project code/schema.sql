-- ============================================================
--  F1 World Championship Tracker — MySQL Schema
-- ============================================================

CREATE DATABASE IF NOT EXISTS f1_tracker;
USE f1_tracker;

-- ------------------------------------------------------------
-- ENGINE SUPPLIERS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS engine_suppliers (
    supplier_id         INT             AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(100)    NOT NULL UNIQUE,
    country             VARCHAR(100)    NOT NULL,
    championship_years  VARCHAR(255)        -- e.g. '2014,2015,2016'
);

-- ------------------------------------------------------------
-- TEAMS (CONSTRUCTORS)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teams (
    team_id             INT             AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(100)    NOT NULL UNIQUE,
    base_location       VARCHAR(100)    NOT NULL,
    supplier_id         INT,
    title_years         VARCHAR(255),       -- e.g. '2014,2015,2016'
    CONSTRAINT fk_team_supplier
        FOREIGN KEY (supplier_id) REFERENCES engine_suppliers(supplier_id)
        ON UPDATE CASCADE ON DELETE SET NULL
);

-- ------------------------------------------------------------
-- DRIVERS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drivers (
    driver_id           INT             AUTO_INCREMENT PRIMARY KEY,
    full_name           VARCHAR(100)    NOT NULL,
    racing_number       INT             NOT NULL UNIQUE,
    nationality         VARCHAR(100)    NOT NULL,
    championship_years  VARCHAR(255)        -- e.g. '2017,2018'
);

-- ------------------------------------------------------------
-- CIRCUITS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS circuits (
    circuit_id          INT             AUTO_INCREMENT PRIMARY KEY,
    track_name          VARCHAR(150)    NOT NULL,
    country             VARCHAR(100)    NOT NULL,
    city                VARCHAR(100)
);

-- ------------------------------------------------------------
-- RACES  (one row per Grand Prix event)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS races (
    race_id             INT             AUTO_INCREMENT PRIMARY KEY,
    season_year         YEAR            NOT NULL,
    round_number        INT             NOT NULL,
    grand_prix_name     VARCHAR(150)    NOT NULL,
    circuit_id          INT             NOT NULL,
    race_date           DATE            NOT NULL,
    CONSTRAINT fk_race_circuit
        FOREIGN KEY (circuit_id) REFERENCES circuits(circuit_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_race UNIQUE (season_year, round_number)
);

-- ------------------------------------------------------------
-- DRIVER-TEAM ASSIGNMENTS  (handles mid-season transfers)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS driver_team_assignments (
    assignment_id       INT             AUTO_INCREMENT PRIMARY KEY,
    driver_id           INT             NOT NULL,
    team_id             INT             NOT NULL,
    season_year         YEAR            NOT NULL,
    effective_from      INT             NOT NULL DEFAULT 1,   -- round number
    effective_to        INT,                                  -- NULL = current
    CONSTRAINT fk_assign_driver
        FOREIGN KEY (driver_id) REFERENCES drivers(driver_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_assign_team
        FOREIGN KEY (team_id)   REFERENCES teams(team_id)
        ON UPDATE CASCADE ON DELETE CASCADE
);

-- ------------------------------------------------------------
-- RACE RESULTS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS results (
    result_id           INT             AUTO_INCREMENT PRIMARY KEY,
    race_id             INT             NOT NULL,
    driver_id           INT             NOT NULL,
    team_id             INT             NOT NULL,
    finish_position     INT,                -- NULL = DNF / DNS
    points_earned       DECIMAL(5,2)    NOT NULL DEFAULT 0.00,
    fastest_lap         BOOLEAN         NOT NULL DEFAULT FALSE,
    dnf                 BOOLEAN         NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_result_race
        FOREIGN KEY (race_id)   REFERENCES races(race_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_result_driver
        FOREIGN KEY (driver_id) REFERENCES drivers(driver_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_result_team
        FOREIGN KEY (team_id)   REFERENCES teams(team_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_result UNIQUE (race_id, driver_id)
);

-- ============================================================
--  VIEWS — convenient query helpers
-- ============================================================

-- Driver standings for any season
CREATE OR REPLACE VIEW v_driver_standings AS
SELECT
    d.driver_id,
    d.full_name,
    d.racing_number,
    d.nationality,
    r.season_year,
    SUM(res.points_earned)  AS total_points,
    COUNT(CASE WHEN res.finish_position = 1 THEN 1 END) AS wins,
    COUNT(res.result_id)    AS races_entered,
    RANK() OVER (
        PARTITION BY r.season_year
        ORDER BY SUM(res.points_earned) DESC
    ) AS standing
FROM results res
JOIN drivers d  ON res.driver_id = d.driver_id
JOIN races   r  ON res.race_id   = r.race_id
GROUP BY d.driver_id, d.full_name, d.racing_number, d.nationality, r.season_year;

-- Constructor standings for any season
CREATE OR REPLACE VIEW v_constructor_standings AS
SELECT
    t.team_id,
    t.name AS team_name,
    r.season_year,
    SUM(res.points_earned)  AS total_points,
    COUNT(CASE WHEN res.finish_position = 1 THEN 1 END) AS wins,
    RANK() OVER (
        PARTITION BY r.season_year
        ORDER BY SUM(res.points_earned) DESC
    ) AS standing
FROM results res
JOIN teams t ON res.team_id = t.team_id
JOIN races r ON res.race_id = r.race_id
GROUP BY t.team_id, t.name, r.season_year;
