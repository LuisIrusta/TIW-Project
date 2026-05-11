CREATE DATABASE TIW_DB;
USE TIW_DB;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP PROCEDURE IF EXISTS sp_assign_project;
DROP PROCEDURE IF EXISTS sp_conclude_project;
DROP TABLE IF EXISTS worked_hours;
DROP TABLE IF EXISTS planned_hours;
DROP TABLE IF EXISTS task_assignments;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS work_packages;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- TABLES
-- =============================================================

CREATE TABLE users (
    id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(80)  NOT NULL,
    last_name       VARCHAR(80)  NOT NULL,
    photo           VARCHAR(255) NULL,
    role            ENUM('administrative','technical') NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT chk_users_names CHECK (
        CHAR_LENGTH(TRIM(first_name)) >= 1 AND CHAR_LENGTH(TRIM(last_name)) >= 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 
CREATE INDEX idx_users_role ON users(role);
 
 
CREATE TABLE projects (
    id                INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    duration_months   SMALLINT UNSIGNED NOT NULL,
    state             ENUM('CREATED','ASSIGNED','CONCLUDED') NOT NULL DEFAULT 'CREATED',
    administrator_id  INT UNSIGNED NOT NULL,
    manager_id        INT UNSIGNED NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 
    CONSTRAINT fk_proj_admin   FOREIGN KEY (administrator_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_proj_manager FOREIGN KEY (manager_id)       REFERENCES users(id) ON DELETE RESTRICT,
 
    CONSTRAINT chk_proj_duration CHECK (duration_months BETWEEN 1 AND 240), -- max 20 years to avoid absurds
    CONSTRAINT chk_proj_title    CHECK (CHAR_LENGTH(TRIM(title)) >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 
CREATE INDEX idx_projects_admin       ON projects(administrator_id);
CREATE INDEX idx_projects_manager     ON projects(manager_id);
CREATE INDEX idx_projects_state       ON projects(state);
CREATE INDEX idx_projects_admin_state ON projects(administrator_id, state);


CREATE TABLE work_packages (
    id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_id    INT UNSIGNED NOT NULL,
    order_number  SMALLINT UNSIGNED NOT NULL,
    title         VARCHAR(200) NOT NULL,
    start_month   SMALLINT UNSIGNED NOT NULL,
    end_month     SMALLINT UNSIGNED NOT NULL,
 
    CONSTRAINT fk_wp_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uq_wp_order   UNIQUE (project_id, order_number),
    CONSTRAINT chk_wp_months CHECK (start_month >= 1 AND start_month <= end_month),
    CONSTRAINT chk_wp_title  CHECK (CHAR_LENGTH(TRIM(title)) >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 
CREATE INDEX idx_wp_project ON work_packages(project_id);
 
 
CREATE TABLE tasks (
    id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    wp_id         INT UNSIGNED NOT NULL,
    order_number  SMALLINT UNSIGNED NOT NULL,
    title         VARCHAR(200) NOT NULL,
    description   TEXT NULL,
    start_month   SMALLINT UNSIGNED NOT NULL,
    end_month     SMALLINT UNSIGNED NOT NULL,
 
    CONSTRAINT fk_task_wp     FOREIGN KEY (wp_id) REFERENCES work_packages(id) ON DELETE CASCADE,
    CONSTRAINT uq_task_order  UNIQUE (wp_id, order_number),
    CONSTRAINT chk_task_months CHECK (start_month >= 1 AND start_month <= end_month),
    CONSTRAINT chk_task_title  CHECK (CHAR_LENGTH(TRIM(title)) >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 
CREATE INDEX idx_task_wp ON tasks(wp_id);
 
 
CREATE TABLE task_assignments (
    task_id          INT UNSIGNED NOT NULL,
    collaborator_id  INT UNSIGNED NOT NULL,
    assigned_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 
    PRIMARY KEY (task_id, collaborator_id),
 
    CONSTRAINT fk_ta_task FOREIGN KEY (task_id)         REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_ta_user FOREIGN KEY (collaborator_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 
CREATE INDEX idx_ta_user ON task_assignments(collaborator_id);
 
 
CREATE TABLE planned_hours (
    task_id      INT UNSIGNED NOT NULL,
    month_index  SMALLINT UNSIGNED NOT NULL,
    hours        SMALLINT UNSIGNED NOT NULL DEFAULT 0,
 
    PRIMARY KEY (task_id, month_index),
 
    CONSTRAINT fk_ph_task   FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT chk_ph_month CHECK (month_index >= 1),
    CONSTRAINT chk_ph_hours CHECK (hours <= 744)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 
 
CREATE TABLE worked_hours (
    task_id          INT UNSIGNED NOT NULL,
    collaborator_id  INT UNSIGNED NOT NULL,
    month_index      SMALLINT UNSIGNED NOT NULL,
    hours            SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 
    PRIMARY KEY (task_id, collaborator_id, month_index),
 
    CONSTRAINT fk_wh_assignment FOREIGN KEY (task_id, collaborator_id)
        REFERENCES task_assignments(task_id, collaborator_id) ON DELETE CASCADE,
    CONSTRAINT chk_wh_month CHECK (month_index >= 1),
    CONSTRAINT chk_wh_hours CHECK (hours <= 744)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 
CREATE INDEX idx_wh_collaborator ON worked_hours(collaborator_id);
CREATE INDEX idx_wh_task_month   ON worked_hours(task_id, month_index);

-- =============================================================
-- TRIGGERS
-- =============================================================

DELIMITER $$

CREATE TRIGGER trg_projects_ins
BEFORE INSERT ON projects FOR EACH ROW
BEGIN
    DECLARE r_admin VARCHAR(20);
    DECLARE r_mgr   VARCHAR(20);
    SELECT role INTO r_admin FROM users WHERE id = NEW.administrator_id;
    SELECT role INTO r_mgr   FROM users WHERE id = NEW.manager_id;
    IF r_admin IS NULL OR r_admin <> 'administrative' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'administrator must exist and have role=administrative';
    END IF;
    IF r_mgr IS NULL OR r_mgr <> 'technical' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'manager must exist and have role=technical';
    END IF;
    IF NEW.state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'new projects must start in CREATED state';
    END IF;
END$$

CREATE TRIGGER trg_projects_upd
BEFORE UPDATE ON projects FOR EACH ROW
BEGIN
    DECLARE r_admin VARCHAR(20);
    DECLARE r_mgr   VARCHAR(20);
    DECLARE bad_tasks INT DEFAULT 0;
 
    IF OLD.state <> 'CREATED' THEN
        IF NEW.title <> OLD.title
           OR NEW.duration_months <> OLD.duration_months
           OR NEW.administrator_id <> OLD.administrator_id
           OR NEW.manager_id <> OLD.manager_id THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'project metadata can only be modified in CREATED state';
        END IF;
    END IF;
 
    -- Re-validate role admin if changed
    IF NEW.administrator_id <> OLD.administrator_id THEN
        SELECT role INTO r_admin FROM users WHERE id = NEW.administrator_id;
        IF r_admin IS NULL OR r_admin <> 'administrative' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'administrator must exist and have role=administrative';
        END IF;
    END IF;
 
    -- Re-validate role manager if changed + conflict with existent collabs
    IF NEW.manager_id <> OLD.manager_id THEN
        SELECT role INTO r_mgr FROM users WHERE id = NEW.manager_id;
        IF r_mgr IS NULL OR r_mgr <> 'technical' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'manager must exist and have role=technical';
        END IF;
        -- New manager cannot already be a collaborator of a task in the project
        IF EXISTS (
            SELECT 1
              FROM task_assignments ta
              JOIN tasks t          ON t.id = ta.task_id
              JOIN work_packages w  ON w.id = t.wp_id
             WHERE w.project_id = NEW.id
               AND ta.collaborator_id = NEW.manager_id
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'new manager is already a collaborator on this project';
        END IF;
    END IF;
 
    -- existent WP should be inside the new interval
    IF NEW.duration_months <> OLD.duration_months THEN
        IF EXISTS (
            SELECT 1 FROM work_packages
             WHERE project_id = NEW.id
               AND end_month > NEW.duration_months
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'cannot shrink project duration: existing WPs exceed new duration';
        END IF;
    END IF;
 
    -- We want to make sure to follow the logical order of transitions
    IF OLD.state <> NEW.state THEN
        IF NOT ((OLD.state = 'CREATED'  AND NEW.state = 'ASSIGNED')
             OR (OLD.state = 'ASSIGNED' AND NEW.state = 'CONCLUDED')) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'invalid state transition';
        END IF;
 
        IF NEW.state = 'ASSIGNED' THEN
            -- Projects should have at least 1 WP
            IF NOT EXISTS (SELECT 1 FROM work_packages WHERE project_id = NEW.id) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'cannot ASSIGN: project has no work packages';
            END IF;
            -- Every WP should have at least 1 task
            IF EXISTS (
                SELECT 1 FROM work_packages w
                 WHERE w.project_id = NEW.id
                   AND NOT EXISTS (SELECT 1 FROM tasks WHERE wp_id = w.id)
            ) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'cannot ASSIGN: some WPs have no tasks';
            END IF;
            -- Every task should have collaborators and expected hours for each month
            SELECT COUNT(*) INTO bad_tasks
              FROM tasks t
              JOIN work_packages w ON w.id = t.wp_id
             WHERE w.project_id = NEW.id
               AND (
                 NOT EXISTS (SELECT 1 FROM task_assignments WHERE task_id = t.id)
                 OR (SELECT COUNT(*) FROM planned_hours WHERE task_id = t.id)
                    <> (t.end_month - t.start_month + 1)
               );
            IF bad_tasks > 0 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'cannot ASSIGN: some tasks lack collaborators or planned hours';
            END IF;
        END IF;
 
        IF NEW.state = 'CONCLUDED' THEN
            SELECT COUNT(*) INTO bad_tasks
              FROM tasks t
              JOIN work_packages w ON w.id = t.wp_id
             WHERE w.project_id = NEW.id
               AND COALESCE((SELECT SUM(hours) FROM worked_hours  WHERE task_id = t.id),0)
                 < COALESCE((SELECT SUM(hours) FROM planned_hours WHERE task_id = t.id),0);
            IF bad_tasks > 0 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'cannot CONCLUDE: worked hours less than planned for some tasks';
            END IF;
        END IF;
    END IF;
END$$

-- WP

CREATE TRIGGER trg_wp_ins
BEFORE INSERT ON work_packages FOR EACH ROW
BEGIN
    DECLARE proj_n SMALLINT UNSIGNED;
    DECLARE proj_state VARCHAR(20);
    SELECT duration_months, state INTO proj_n, proj_state
      FROM projects WHERE id = NEW.project_id;
    IF proj_state IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced project does not exist';
    END IF;
    IF proj_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cannot add WP: project not in CREATED state';
    END IF;
    IF NEW.end_month > proj_n THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'WP end_month exceeds project duration';
    END IF;
END$$
 
CREATE TRIGGER trg_wp_upd
BEFORE UPDATE ON work_packages FOR EACH ROW
BEGIN
    DECLARE proj_n SMALLINT UNSIGNED;
    DECLARE proj_state VARCHAR(20);
    SELECT duration_months, state INTO proj_n, proj_state
      FROM projects WHERE id = NEW.project_id;
    IF proj_state IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced project does not exist';
    END IF;
    IF proj_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cannot modify WP: project not in CREATED state';
    END IF;
    IF NEW.end_month > proj_n THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'WP end_month exceeds project duration';
    END IF;
    -- If the interval of the WP changes, the tasks should fit inside
    IF NEW.start_month <> OLD.start_month OR NEW.end_month <> OLD.end_month THEN
        IF EXISTS (
            SELECT 1 FROM tasks
             WHERE wp_id = NEW.id
               AND (start_month < NEW.start_month OR end_month > NEW.end_month)
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'cannot modify WP interval: existing tasks fall outside';
        END IF;
    END IF;
END$$
 
CREATE TRIGGER trg_wp_del
BEFORE DELETE ON work_packages FOR EACH ROW
BEGIN
    DECLARE proj_state VARCHAR(20);
    SELECT state INTO proj_state FROM projects WHERE id = OLD.project_id;
    IF proj_state IS NOT NULL AND proj_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cannot delete WP: project not in CREATED state';
    END IF;
END$$

-- Tasks

CREATE TRIGGER trg_task_ins
BEFORE INSERT ON tasks FOR EACH ROW
BEGIN
    DECLARE wp_s, wp_e SMALLINT UNSIGNED;
    DECLARE proj_state VARCHAR(20);
    SELECT w.start_month, w.end_month, p.state
      INTO wp_s, wp_e, proj_state
      FROM work_packages w JOIN projects p ON p.id = w.project_id
     WHERE w.id = NEW.wp_id;
    IF proj_state IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced WP or project does not exist';
    END IF;
    IF proj_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cannot add task: project not in CREATED state';
    END IF;
    IF NEW.start_month < wp_s OR NEW.end_month > wp_e THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'task interval outside its WP interval';
    END IF;
END$$
 
CREATE TRIGGER trg_task_upd
BEFORE UPDATE ON tasks FOR EACH ROW
BEGIN
    DECLARE wp_s, wp_e SMALLINT UNSIGNED;
    DECLARE proj_state VARCHAR(20);
    SELECT w.start_month, w.end_month, p.state
      INTO wp_s, wp_e, proj_state
      FROM work_packages w JOIN projects p ON p.id = w.project_id
     WHERE w.id = NEW.wp_id;
    IF proj_state IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced WP or project does not exist';
    END IF;
    IF proj_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cannot modify task: project not in CREATED state';
    END IF;
    IF NEW.start_month < wp_s OR NEW.end_month > wp_e THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'task interval outside its WP interval';
    END IF;
    -- If the task interval changes, the existing planned hours should fit
    IF NEW.start_month <> OLD.start_month OR NEW.end_month <> OLD.end_month THEN
        IF EXISTS (
            SELECT 1 FROM planned_hours
             WHERE task_id = NEW.id
               AND (month_index < NEW.start_month OR month_index > NEW.end_month)
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'cannot modify task interval: existing planned hours fall outside';
        END IF;
    END IF;
END$$
 
CREATE TRIGGER trg_task_del
BEFORE DELETE ON tasks FOR EACH ROW
BEGIN
    DECLARE proj_state VARCHAR(20);
    SELECT p.state INTO proj_state
      FROM projects p JOIN work_packages w ON w.project_id = p.id
     WHERE w.id = OLD.wp_id;
    IF proj_state IS NOT NULL AND proj_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cannot delete task: project not in CREATED state';
    END IF;
END$$

-- Task assignments

CREATE TRIGGER trg_assignment_ins
BEFORE INSERT ON task_assignments FOR EACH ROW
BEGIN
    DECLARE mgr_id INT UNSIGNED;
    DECLARE coll_role VARCHAR(20);
    DECLARE proj_state VARCHAR(20);
 
    SELECT p.manager_id, p.state INTO mgr_id, proj_state
      FROM projects p
      JOIN work_packages w ON w.project_id = p.id
      JOIN tasks t         ON t.wp_id = w.id
     WHERE t.id = NEW.task_id;
 
    IF proj_state IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced task/project does not exist';
    END IF;
    IF proj_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'cannot assign collaborator: project not in CREATED state';
    END IF;
 
    SELECT role INTO coll_role FROM users WHERE id = NEW.collaborator_id;
    IF coll_role IS NULL OR coll_role <> 'technical' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'collaborator must exist and have role=technical';
    END IF;
 
    IF NEW.collaborator_id = mgr_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'manager cannot self-assign as collaborator';
    END IF;
END$$
 
CREATE TRIGGER trg_assignment_del
BEFORE DELETE ON task_assignments FOR EACH ROW
BEGIN
    DECLARE proj_state VARCHAR(20);
    SELECT p.state INTO proj_state
      FROM projects p
      JOIN work_packages w ON w.project_id = p.id
      JOIN tasks t         ON t.wp_id = w.id
     WHERE t.id = OLD.task_id;
    IF proj_state IS NOT NULL AND proj_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'cannot remove assignment: project not in CREATED state';
    END IF;
END$$

-- Planned hours

CREATE TRIGGER trg_planned_ins
BEFORE INSERT ON planned_hours FOR EACH ROW
BEGIN
    DECLARE t_s, t_e SMALLINT UNSIGNED;
    DECLARE proj_state VARCHAR(20);
    SELECT t.start_month, t.end_month, p.state
      INTO t_s, t_e, proj_state
      FROM tasks t
      JOIN work_packages w ON w.id = t.wp_id
      JOIN projects p      ON p.id = w.project_id
     WHERE t.id = NEW.task_id;
    IF proj_state IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced task/project does not exist';
    END IF;
    IF proj_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'cannot set planned hours: project not in CREATED state';
    END IF;
    IF NEW.month_index < t_s OR NEW.month_index > t_e THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'planned month_index outside task interval';
    END IF;
END$$
 
CREATE TRIGGER trg_planned_upd
BEFORE UPDATE ON planned_hours FOR EACH ROW
BEGIN
    DECLARE t_s, t_e SMALLINT UNSIGNED;
    DECLARE proj_state VARCHAR(20);
    SELECT t.start_month, t.end_month, p.state
      INTO t_s, t_e, proj_state
      FROM tasks t
      JOIN work_packages w ON w.id = t.wp_id
      JOIN projects p      ON p.id = w.project_id
     WHERE t.id = NEW.task_id;
    IF proj_state IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced task/project does not exist';
    END IF;
    IF proj_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'cannot modify planned hours: project not in CREATED state';
    END IF;
    IF NEW.month_index < t_s OR NEW.month_index > t_e THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'planned month_index outside task interval';
    END IF;
END$$

-- Worked hours

CREATE TRIGGER trg_worked_ins
BEFORE INSERT ON worked_hours FOR EACH ROW
BEGIN
    DECLARE t_s, t_e SMALLINT UNSIGNED;
    DECLARE proj_state VARCHAR(20);
    SELECT t.start_month, t.end_month, p.state
      INTO t_s, t_e, proj_state
      FROM tasks t
      JOIN work_packages w ON w.id = t.wp_id
      JOIN projects p      ON p.id = w.project_id
     WHERE t.id = NEW.task_id;
    IF proj_state IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced task/project does not exist';
    END IF;
    IF proj_state <> 'ASSIGNED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'cannot report worked hours: project not in ASSIGNED state';
    END IF;
    IF NEW.month_index < t_s OR NEW.month_index > t_e THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'worked month_index outside task interval';
    END IF;
END$$
 
CREATE TRIGGER trg_worked_upd
BEFORE UPDATE ON worked_hours FOR EACH ROW
BEGIN
    DECLARE t_s, t_e SMALLINT UNSIGNED;
    DECLARE proj_state VARCHAR(20);
    SELECT t.start_month, t.end_month, p.state
      INTO t_s, t_e, proj_state
      FROM tasks t
      JOIN work_packages w ON w.id = t.wp_id
      JOIN projects p      ON p.id = w.project_id
     WHERE t.id = NEW.task_id;
    IF proj_state IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced task/project does not exist';
    END IF;
    IF proj_state <> 'ASSIGNED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'cannot modify worked hours: project not in ASSIGNED state';
    END IF;
    IF NEW.month_index < t_s OR NEW.month_index > t_e THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'worked month_index outside task interval';
    END IF;
END$$


-- =============================================================
-- STORED PROCEDURES — state transitions
-- =============================================================

CREATE PROCEDURE sp_assign_project(
    IN p_project_id INT UNSIGNED,
    IN p_user_id    INT UNSIGNED
)
BEGIN
    DECLARE current_mgr   INT UNSIGNED;
    DECLARE current_state VARCHAR(20);
 
    SELECT manager_id, state INTO current_mgr, current_state
      FROM projects WHERE id = p_project_id FOR UPDATE;
 
    IF current_mgr IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'project not found';
    END IF;
    IF current_mgr <> p_user_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'only the project manager can assign';
    END IF;
    IF current_state <> 'CREATED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'project must be in CREATED state to be assigned';
    END IF;
    
    UPDATE projects SET state = 'ASSIGNED' WHERE id = p_project_id;
END$$
 
CREATE PROCEDURE sp_conclude_project(
    IN p_project_id INT UNSIGNED,
    IN p_user_id    INT UNSIGNED
)
BEGIN
    DECLARE current_mgr   INT UNSIGNED;
    DECLARE current_state VARCHAR(20);
 
    SELECT manager_id, state INTO current_mgr, current_state
      FROM projects WHERE id = p_project_id FOR UPDATE;
 
    IF current_mgr IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'project not found';
    END IF;
    IF current_mgr <> p_user_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'only the project manager can conclude';
    END IF;
    IF current_state <> 'ASSIGNED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'project must be in ASSIGNED state to be concluded';
    END IF;
 
    UPDATE projects SET state = 'CONCLUDED' WHERE id = p_project_id;
END$$
 
DELIMITER ;

-- =============================================================
-- VIEWS
-- =============================================================

CREATE OR REPLACE VIEW v_task_completion AS
SELECT
    t.id AS task_id,
    p.id AS project_id,
    COALESCE((SELECT SUM(hours) FROM planned_hours WHERE task_id = t.id),0) AS planned,
    COALESCE((SELECT SUM(hours) FROM worked_hours  WHERE task_id = t.id),0) AS worked
FROM tasks t
JOIN work_packages w ON w.id = t.wp_id
JOIN projects p      ON p.id = w.project_id;